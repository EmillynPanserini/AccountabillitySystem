package org.example.identidade.sessao;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.example.Main;
import org.example.identidade.IdentityPasswords;
import org.example.identidade.HttpsTestFixture;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.web.FilterChainProxy;

import javax.net.ssl.SSLContext;
import javax.sql.DataSource;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

class SessionHttpsTest {
    @TempDir static Path temporary;
    private static ServletWebServerApplicationContext application;
    private static SSLContext tls;
    private static URI base;
    private static final ObjectMapper JSON = new ObjectMapper();

    @BeforeAll
    static void startHttpsWithoutDatabase() throws Exception {
        var https = new HttpsTestFixture(temporary);
        tls = https.tls();
        application = https.start(new String[]{"session-https-test"}, Main.class, Fixtures.class);
        base = URI.create("https://127.0.0.1:" + application.getWebServer().getPort());
    }

    @AfterAll
    static void stopHttps() { if (application != null) application.close(); }

    @Test
    void anonymousSessionHasProtectedCookieAndCsrfAndDefaultIdentityFailsClosed() throws Exception {
        var browser = new Browser();
        var response = browser.get("/api/v1/sessao");
        assertEquals(200, response.statusCode());
        JsonNode session = JSON.readTree(response.body());
        assertFalse(session.get("autenticado").asBoolean());
        assertEquals("X-CSRF-TOKEN", session.get("csrfHeader").asText());
        assertTrue(response.headers().firstValue("Cache-Control").orElse("").contains("no-store"));
        String cookie = response.headers().firstValue("Set-Cookie").orElseThrow();
        assertTrue(cookie.startsWith("JSESSIONID="));
        assertTrue(cookie.contains("HttpOnly") && cookie.contains("Secure") && cookie.contains("SameSite=Lax"));
        assertEquals(401, browser.get("/api/v1/test/protected").statusCode());
        assertEquals(403, browser.post("/api/v1/login", "identificador=fixture&senha=fixture-password", null).statusCode());
        assertEquals(403, browser.post("/api/v1/login", "identificador=fixture&senha=fixture-password", "invalid").statusCode());
        assertEquals(403, browser.post("/api/v1/login", "identificador=fixture&senha=fixture-password&_csrf="
                + URLEncoder.encode(session.get("csrfToken").asText(), StandardCharsets.UTF_8), null).statusCode());
        assertTrue(application.getBeansOfType(DataSource.class).isEmpty());
        var productionUsers = application.getBean("identityUsers", UserDetailsService.class);
        assertThrows(UsernameNotFoundException.class, () -> productionUsers.loadUserByUsername("fixture"));
        assertEquals(200, browser.get("/api/status").statusCode());
    }

    @Test
    void filterRejectsPlainHttpBeforeCreatingSession() throws Exception {
        var request = new MockHttpServletRequest("GET", "/api/v1/sessao");
        request.setServletPath("/api/v1/sessao");
        request.setSecure(false);
        var response = new MockHttpServletResponse();
        application.getBean("springSecurityFilterChain", FilterChainProxy.class).doFilter(request, response,
                (ignoredRequest, ignoredResponse) -> fail("HTTP não pode alcançar o endpoint de sessão."));
        assertEquals(403, response.getStatus());
        assertNull(request.getSession(false));
    }

    @Test
    void loginRotatesSessionAndCsrfLogoutInvalidatesBothWithoutReplay() throws Exception {
        var browser = new Browser();
        String beforeToken = browser.csrf();
        String beforeCookie = browser.cookie();
        assertEquals(204, browser.login("fixture", "fixture-password", beforeToken).statusCode());
        assertFalse(beforeCookie.equals(browser.cookie()));
        var authenticated = JSON.readTree(browser.get("/api/v1/sessao").body());
        assertTrue(authenticated.get("autenticado").asBoolean());
        assertEquals("fixture", authenticated.get("identificador").asText());
        assertEquals(200, browser.get("/api/v1/test/protected").statusCode());
        assertEquals(403, browser.post("/api/v1/test/protected", "", beforeToken).statusCode());
        String currentToken = browser.csrf();
        assertEquals(204, browser.post("/api/v1/test/protected", "", currentToken).statusCode());
        assertEquals(403, browser.post("/api/v1/logout", "", null).statusCode());
        String authenticatedCookie = browser.cookie();
        assertEquals(204, browser.post("/api/v1/logout", "", currentToken).statusCode());
        assertEquals(401, requestWithOldCookie(authenticatedCookie, "/api/v1/test/protected").statusCode());
        assertEquals(401, browser.get("/api/v1/test/protected").statusCode());
        String afterToken = browser.csrf();
        assertFalse(currentToken.equals(afterToken));
        assertEquals(403, browser.login("fixture", "fixture-password", currentToken).statusCode());
        assertFalse(JSON.readTree(browser.get("/api/v1/sessao").body()).get("autenticado").asBoolean());
    }

    @Test
    void wrongPasswordAndMissingIdentityHaveSameErrorAndLimitsIgnoreSpoofedForwardedIp() throws Exception {
        var browser = new Browser();
        String token = browser.csrf();
        var wrong = browser.login("fixture", "wrong-fixture-password", token);
        var absent = browser.login("absent-fixture", "wrong-fixture-password", token);
        assertEquals(401, wrong.statusCode());
        assertEquals(401, absent.statusCode());
        assertEquals(wrong.body(), absent.body());
        for (int index = 0; index < 5; index++) assertEquals(401, browser.login("limited-fixture", "wrong", token).statusCode());
        var blocked = browser.send("/api/v1/login", "identificador=limited-fixture&senha=wrong", token, "203.0.113.7");
        assertEquals(429, blocked.statusCode());
        assertEquals("900", blocked.headers().firstValue("Retry-After").orElseThrow());
        assertFalse(JSON.readTree(browser.get("/api/v1/sessao").body()).get("autenticado").asBoolean());
    }

    @Test
    void servletExpiresIdleSessionWithTestOnlyReducedInterval() throws Exception {
        var browser = new Browser();
        assertEquals(204, browser.login("fixture", "fixture-password", browser.csrf()).statusCode());
        assertEquals("1800", browser.get("/api/v1/test/timeout").body());
        String token = browser.csrf();
        assertEquals(204, browser.post("/api/v1/test/short-idle", "", token).statusCode());
        Thread.sleep(1500);
        assertEquals(401, browser.get("/api/v1/test/protected").statusCode());
        assertEquals(403, browser.post("/api/v1/test/protected", "", token).statusCode());
        assertFalse(JSON.readTree(browser.get("/api/v1/sessao").body()).get("autenticado").asBoolean());
        assertEquals(204, browser.login("fixture", "fixture-password", browser.csrf()).statusCode());
    }

    @Test
    void credentialsCannotUseHttpBasicAndUnknownResourcesRemainDenied() throws Exception {
        var browser = new Browser();
        var basic = HttpRequest.newBuilder(base.resolve("/api/v1/test/protected"))
                .header("Authorization", "Basic " + java.util.Base64.getEncoder().encodeToString("fixture:fixture-password".getBytes(StandardCharsets.UTF_8))).GET().build();
        assertEquals(401, browser.client.send(basic, HttpResponse.BodyHandlers.ofString()).statusCode());
        assertEquals(204, browser.login("fixture", "fixture-password", browser.csrf()).statusCode());
        assertEquals(403, browser.get("/outside-api").statusCode());
    }

    private static HttpResponse<String> requestWithOldCookie(String cookie, String path) throws Exception {
        return HttpClient.newBuilder().sslContext(tls).build().send(HttpRequest.newBuilder(base.resolve(path))
                .header("Cookie", "JSESSIONID=" + cookie).GET().build(), HttpResponse.BodyHandlers.ofString());
    }

    private static final class Browser {
        private final CookieManager cookies = new CookieManager(null, CookiePolicy.ACCEPT_ORIGINAL_SERVER);
        private final HttpClient client = HttpClient.newBuilder().sslContext(tls).cookieHandler(cookies).connectTimeout(Duration.ofSeconds(5)).build();
        String cookie() { return cookies.getCookieStore().getCookies().stream().filter(cookie -> cookie.getName().equals("JSESSIONID")).findFirst().orElseThrow().getValue(); }
        String csrf() throws Exception { return JSON.readTree(get("/api/v1/sessao").body()).get("csrfToken").asText(); }
        HttpResponse<String> get(String path) throws Exception { return client.send(HttpRequest.newBuilder(base.resolve(path)).GET().build(), HttpResponse.BodyHandlers.ofString()); }
        HttpResponse<String> login(String identifier, String password, String csrf) throws Exception {
            return post("/api/v1/login", "identificador=" + URLEncoder.encode(identifier, StandardCharsets.UTF_8) + "&senha=" + URLEncoder.encode(password, StandardCharsets.UTF_8), csrf);
        }
        HttpResponse<String> post(String path, String body, String csrf) throws Exception { return send(path, body, csrf, null); }
        HttpResponse<String> send(String path, String body, String csrf, String forwarded) throws Exception {
            var request = HttpRequest.newBuilder(base.resolve(path)).header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(body));
            if (csrf != null) request.header("X-CSRF-TOKEN", csrf);
            if (forwarded != null) request.header("X-Forwarded-For", forwarded);
            return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class Fixtures {
        @Bean @Primary UserDetailsService fixtureUsers() {
            String hash = IdentityPasswords.encoder().encode("fixture-password");
            return username -> {
                if (!"fixture".equals(JdbcIdentityUsers.normalize(username))) throw new UsernameNotFoundException("Credenciais inválidas.");
                return User.withUsername("fixture").password(hash).authorities("ADMINISTRAR_IDENTIDADES", "CRIAR_EMPRESA").build();
            };
        }
        @Bean TestEndpoints testEndpoints() { return new TestEndpoints(); }
    }

    @RestController
    @Profile("session-https-test")
    static class TestEndpoints {
        @GetMapping("/api/v1/test/protected") String protectedGet() { return "autorizado"; }
        @PostMapping("/api/v1/test/protected") ResponseEntity<Void> protectedPost() { return ResponseEntity.noContent().build(); }
        @GetMapping("/api/v1/test/timeout") int timeout(HttpServletRequest request) { return request.getSession(false).getMaxInactiveInterval(); }
        @PostMapping("/api/v1/test/short-idle") ResponseEntity<Void> shortIdle(HttpServletRequest request) {
            request.getSession(false).setMaxInactiveInterval(1);
            return ResponseEntity.noContent().build();
        }
    }
}
