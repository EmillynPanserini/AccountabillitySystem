package org.example.identidade;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.Main;
import org.example.identidade.sessao.JdbcIdentityUsers;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext;
import org.springframework.security.core.userdetails.UserDetailsService;

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
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class IdentityLoginPersistenceIT {
    @TempDir static Path temporary;
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String IDENTIFIER = "admin-integracao@example.invalid";
    private static HttpsTestFixture https;
    private static ServletWebServerApplicationContext application;
    private static URI base;

    @BeforeAll
    static void persistBootstrapAndStartRealJdbcAuthentication() throws Exception {
        var target = new SyntheticBootstrapTarget(System.getenv());
        DataSource migrator = target.dataSource(true);
        Flyway.configure().dataSource(migrator).locations("classpath:db/migration/t06")
                .defaultSchema("t06_history").schemas("t06_history").cleanDisabled(true)
                .baselineOnMigrate(false).outOfOrder(false).load().migrate();
        try (var connection = migrator.getConnection(); var statement = connection.createStatement()) {
            statement.execute("TRUNCATE t06_identity.audit, t06_identity.capability, t06_identity.bootstrap, t06_identity.identity");
            statement.execute("INSERT INTO t06_identity.bootstrap(singleton) VALUES (true)");
        }
        new BootstrapService(target.dataSource(false)).createAdministrator(
                new BootstrapRequest("operador-ficticio", IDENTIFIER, "verificacao-sintetica-integracao"),
                "senha-ficticia-integracao".toCharArray());
        https = new HttpsTestFixture(temporary);
        startContext();
    }

    private static void startContext() {
        application = https.start(new String[]{"t06-sintetico"}, Main.class);
        base = URI.create("https://127.0.0.1:" + application.getWebServer().getPort());
    }

    @AfterAll
    static void stopContext() { if (application != null) application.close(); }

    @Test
    void persistedAdministratorAuthenticatesThroughJdbcAndReceivesExactCapabilities() throws Exception {
        assertEquals(1, application.getBeansOfType(UserDetailsService.class).size());
        assertInstanceOf(JdbcIdentityUsers.class, application.getBean(UserDetailsService.class));
        var browser = new Browser();
        String csrf = browser.csrf();
        String oldCookie = browser.cookie();
        assertEquals(204, browser.login(IDENTIFIER.toUpperCase(java.util.Locale.ROOT), "senha-ficticia-integracao", csrf).statusCode());
        assertFalse(oldCookie.equals(browser.cookie()));
        JsonNode session = browser.session();
        assertTrue(session.get("autenticado").asBoolean());
        assertEquals(IDENTIFIER, session.get("identificador").asText());
        assertEquals(List.of("ADMINISTRAR_IDENTIDADES", "CRIAR_EMPRESA"), JSON.convertValue(session.get("capacidades"), List.class));
    }

    @Test
    void wrongAndMissingCredentialsAreGenericAndCsrfProtectsLoginAndLogout() throws Exception {
        var browser = new Browser();
        String csrf = browser.csrf();
        assertEquals(403, browser.login(IDENTIFIER, "senha-ficticia-integracao", null).statusCode());
        var wrong = browser.login(IDENTIFIER, "senha-incorreta-ficticia", csrf);
        var missing = browser.login("ausente@example.invalid", "senha-incorreta-ficticia", csrf);
        assertEquals(401, wrong.statusCode());
        assertEquals(401, missing.statusCode());
        assertEquals(wrong.body(), missing.body());
        assertEquals(204, browser.login(IDENTIFIER, "senha-ficticia-integracao", csrf).statusCode());
        assertEquals(403, browser.post("/api/v1/logout", "", csrf).statusCode());
        assertEquals(403, browser.post("/api/v1/logout", "", null).statusCode());
        String newCsrf = browser.csrf();
        String oldCookie = browser.cookie();
        assertEquals(204, browser.post("/api/v1/logout", "", newCsrf).statusCode());
        assertFalse(browser.session().get("autenticado").asBoolean());
        assertEquals(401, rawClient().send(HttpRequest.newBuilder(base.resolve("/api/v1/protegido-inexistente"))
                .header("Cookie", "JSESSIONID=" + oldCookie).GET().build(), HttpResponse.BodyHandlers.ofString()).statusCode());
        assertEquals(403, browser.login(IDENTIFIER, "senha-ficticia-integracao", newCsrf).statusCode());
    }

    @Test
    void identitySurvivesNewApplicationContextButOldSessionDoesNot() throws Exception {
        var before = new Browser();
        assertEquals(204, before.login(IDENTIFIER, "senha-ficticia-integracao", before.csrf()).statusCode());
        String previousCookie = before.cookie();
        application.close();
        startContext();
        var response = rawClient().send(HttpRequest.newBuilder(base.resolve("/api/v1/sessao"))
                .header("Cookie", "JSESSIONID=" + previousCookie).GET().build(), HttpResponse.BodyHandlers.ofString());
        assertFalse(JSON.readTree(response.body()).get("autenticado").asBoolean());
        var after = new Browser();
        assertEquals(204, after.login(IDENTIFIER, "senha-ficticia-integracao", after.csrf()).statusCode());
        assertTrue(after.session().get("autenticado").asBoolean());
        assertEquals(IDENTIFIER, after.session().get("identificador").asText());
        assertThrows(IllegalStateException.class, () -> new BootstrapService(new SyntheticBootstrapTarget(System.getenv()).dataSource(false))
                .createAdministrator(new BootstrapRequest("outro-operador", "outro-admin", "verificacao-ficticia"), "outra-senha-ficticia".toCharArray()));
    }

    @Test
    void authenticationUsesReadOnlyRoleWithoutBootstrapAuditOrDdlPrivileges() throws Exception {
        try (var connection = application.getBean(DataSource.class).getConnection(); var statement = connection.createStatement()) {
            try (var result = statement.executeQuery("SELECT current_user, current_setting('server_version_num')::int")) {
                assertTrue(result.next());
                assertEquals("t06_identity_reader", result.getString(1));
                assertTrue(result.getInt(2) >= 170000 && result.getInt(2) < 180000);
            }
            for (String forbidden : List.of("UPDATE t06_identity.identity SET login=login",
                    "INSERT INTO t06_identity.capability(identity_id,name) SELECT id,'CRIAR_EMPRESA' FROM t06_identity.identity",
                    "SELECT * FROM t06_identity.audit", "UPDATE t06_identity.bootstrap SET closed_at=NULL",
                    "CREATE TABLE t06_identity.forbidden(id int)", "UPDATE public.t06_target SET run=run")) {
                assertEquals("42501", assertThrows(SQLException.class, () -> statement.execute(forbidden)).getSQLState());
            }
        }
    }

    @Test
    void tamperedDockerTargetAndAlternateCredentialsAreRejectedBeforeJdbc() {
        var environment = new HashMap<>(System.getenv());
        environment.put("T06_CONTAINER", "0".repeat(64));
        var source = new SyntheticBootstrapTarget(environment).authenticationDataSource();
        assertThrows(SQLException.class, source::getConnection);
        var real = new SyntheticBootstrapTarget(System.getenv()).authenticationDataSource();
        assertThrows(SQLException.class, () -> real.getConnection("postgres", "not-used"));
        var wrongDatabase = new HashMap<>(System.getenv());
        wrongDatabase.put("T06_DATABASE", "acct_t06_" + "0".repeat(32));
        assertThrows(IllegalArgumentException.class, () -> new SyntheticBootstrapTarget(wrongDatabase));
    }

    private static HttpClient rawClient() { return HttpClient.newBuilder().sslContext(https.tls()).build(); }

    private static final class Browser {
        private final CookieManager cookies = new CookieManager(null, CookiePolicy.ACCEPT_ORIGINAL_SERVER);
        private final HttpClient client = HttpClient.newBuilder().sslContext(https.tls()).cookieHandler(cookies).build();
        String cookie() { return cookies.getCookieStore().getCookies().stream().filter(cookie -> cookie.getName().equals("JSESSIONID")).findFirst().orElseThrow().getValue(); }
        JsonNode session() throws Exception {
            var response = client.send(HttpRequest.newBuilder(base.resolve("/api/v1/sessao")).GET().build(), HttpResponse.BodyHandlers.ofString());
            assertEquals(200, response.statusCode());
            assertTrue(response.headers().firstValue("Cache-Control").orElse("").contains("no-store"));
            return JSON.readTree(response.body());
        }
        String csrf() throws Exception { return session().get("csrfToken").asText(); }
        HttpResponse<String> login(String identifier, String password, String csrf) throws Exception {
            return post("/api/v1/login", "identificador=" + URLEncoder.encode(identifier, StandardCharsets.UTF_8)
                    + "&senha=" + URLEncoder.encode(password, StandardCharsets.UTF_8), csrf);
        }
        HttpResponse<String> post(String path, String body, String csrf) throws Exception {
            var request = HttpRequest.newBuilder(base.resolve(path)).header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(body));
            if (csrf != null) request.header("X-CSRF-TOKEN", csrf);
            return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
        }
    }
}
