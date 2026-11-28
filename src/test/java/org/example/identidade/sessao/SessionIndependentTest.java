package org.example.identidade.sessao;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockServletContext;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import javax.sql.DataSource;
import java.time.Clock;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SessionIndependentTest {
    private AnnotationConfigWebApplicationContext context;

    @AfterEach
    void closeContext() {
        if (context != null) context.close();
    }

    @Test
    void blockedIpCannotExhaustMemoryUsingAlreadyRejectedAttempts() {
        var attempts = new LoginAttempts(Clock.systemUTC());
        for (int index = 0; index < 30; index++) {
            var reservation = attempts.reserve("initial-" + index, "192.0.2.1");
            assertNotNull(reservation);
            attempts.finish(reservation, true);
        }
        for (int index = 0; index < 10_000; index++) {
            assertNull(attempts.reserve("rejected-" + index, "192.0.2.1"));
        }
        assertNotNull(attempts.reserve("legitimate", "192.0.2.2"),
                "An already blocked IP must not consume capacity with rejected attempts.");
    }

    @Test
    void defaultAuthenticationFailsClosedThroughSecurityChainWithoutDatabase() throws Exception {
        var mvc = mvc();
        var sessionResult = mvc.perform(get("/api/v1/sessao").servletPath("/api/v1/sessao").secure(true))
                .andExpect(status().isOk()).andReturn();
        var session = (MockHttpSession) sessionResult.getRequest().getSession(false);
        String csrf = new ObjectMapper().readTree(sessionResult.getResponse().getContentAsString()).get("csrfToken").asText();
        var result = mvc.perform(post("/api/v1/login").servletPath("/api/v1/login").secure(true).session(session)
                        .contentType("application/x-www-form-urlencoded")
                        .content("identificador=unknown&senha=fictitious")
                        .param("identificador", "unknown").param("senha", "fictitious")
                        .header("X-CSRF-TOKEN", csrf))
                .andExpect(status().isUnauthorized()).andReturn();
        assertEquals("{\"erro\":\"Credenciais inválidas.\"}", result.getResponse().getContentAsString());
        assertTrue(context.getBeansOfType(DataSource.class).isEmpty());
        assertEquals(1, context.getBeansOfType(UserDetailsService.class).size());
        assertThrows(UsernameNotFoundException.class,
                () -> context.getBean(UserDetailsService.class).loadUserByUsername("user"));
        mvc.perform(get("/api/v1/sessao").servletPath("/api/v1/sessao").secure(true).session(session))
                .andExpect(result1 -> assertFalse(new ObjectMapper().readTree(result1.getResponse().getContentAsString())
                        .get("autenticado").asBoolean()));
    }

    @Test
    void forwardedHeadersCannotUpgradePlainHttpOrCreateSession() throws Exception {
        var result = mvc().perform(get("/api/v1/sessao").servletPath("/api/v1/sessao").secure(false)
                        .header("X-Forwarded-Proto", "https").header("Forwarded", "proto=https;for=192.0.2.9"))
                .andExpect(status().isForbidden()).andReturn();
        assertNull(result.getRequest().getSession(false));
        assertFalse(result.getResponse().containsHeader("Set-Cookie"));
    }

    @Test
    void csrfProtectsLoginAndLogoutAndGetDoesNotLogout() throws Exception {
        var mvc = mvc();
        mvc.perform(post("/api/v1/login").servletPath("/api/v1/login").secure(true)
                        .contentType("application/x-www-form-urlencoded").content("identificador=user&senha=fictitious"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/logout").servletPath("/api/v1/logout").secure(true))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/logout").servletPath("/api/v1/logout").secure(true))
                .andExpect(status().isUnauthorized());
    }

    private MockMvc mvc() {
        context = new AnnotationConfigWebApplicationContext();
        context.setServletContext(new MockServletContext());
        context.register(SecurityFixture.class);
        context.refresh();
        return MockMvcBuilders.webAppContextSetup(context)
                .addFilters(context.getBean("springSecurityFilterChain", FilterChainProxy.class)).build();
    }

    @TestConfiguration(proxyBeanMethods = false)
    @EnableWebSecurity
    @EnableWebMvc
    @Import({SessionSecurity.class, SessionController.class})
    static class SecurityFixture {}
}
