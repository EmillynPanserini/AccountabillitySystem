package org.example.identidade;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.Main;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext;

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

import static org.junit.jupiter.api.Assertions.*;

class IdentityLoginIndependentIT {
    @TempDir static Path temporary;
    private static final String IDENTIFIER = "admin-independente@example.invalid";
    private static final ObjectMapper JSON = new ObjectMapper();
    private static DataSource migrator;
    private static ServletWebServerApplicationContext application;
    private static HttpsTestFixture https;
    private static URI base;

    @BeforeAll
    static void createOwnFixtureOnAttestedSyntheticTarget() throws Exception {
        var target = new SyntheticBootstrapTarget(System.getenv());
        migrator = target.dataSource(true);
        Flyway.configure().dataSource(migrator).locations("classpath:db/migration/t06")
                .defaultSchema("t06_history").schemas("t06_history").cleanDisabled(true)
                .baselineOnMigrate(false).outOfOrder(false).load().migrate();
        try (var connection = migrator.getConnection(); var statement = connection.createStatement()) {
            statement.execute("TRUNCATE t06_identity.audit, t06_identity.capability, t06_identity.bootstrap, t06_identity.identity");
            statement.execute("INSERT INTO t06_identity.bootstrap(singleton) VALUES (true)");
        }
        new BootstrapService(target.dataSource(false)).createAdministrator(
                new BootstrapRequest("operador-ficticio", IDENTIFIER, "verificacao-independente"),
                "senha-ficticia-independente".toCharArray());
        https = new HttpsTestFixture(temporary);
        application = https.start(new String[]{"t06-sintetico"}, Main.class);
        base = URI.create("https://127.0.0.1:" + application.getWebServer().getPort());
    }

    @AfterAll
    static void closeContext() {
        if (application != null) application.close();
    }

    @Test
    void readerCannotAssumePrivilegedRolesAndSqlIdentifierDoesNotAuthenticate() throws Exception {
        try (var connection = application.getBean(DataSource.class).getConnection();
             var statement = connection.createStatement()) {
            for (String role : new String[]{"postgres", "t06_bootstrap"}) {
                assertEquals("42501", assertThrows(SQLException.class,
                        () -> statement.execute("SET ROLE " + role)).getSQLState());
            }
        }
        var browser = new Browser();
        var response = browser.login("' OR '1'='1", browser.csrf());
        assertEquals(401, response.statusCode());
        assertEquals("{\"erro\":\"Credenciais inválidas.\"}", response.body());
        assertFalse(browser.session().get("autenticado").asBoolean());
        try (var connection = migrator.getConnection(); var statement = connection.createStatement();
             var result = statement.executeQuery("SELECT count(*) FROM t06_identity.identity")) {
            assertTrue(result.next());
            assertEquals(1, result.getInt(1));
        }
    }

    @Test
    void databasePermissionFailureIsGenericAndRecoveryDoesNotGrantExtraAccess() throws Exception {
        try (var connection = migrator.getConnection(); var statement = connection.createStatement()) {
            statement.execute("REVOKE SELECT ON t06_identity.identity FROM t06_identity_reader");
            try {
                var browser = new Browser();
                String csrf = browser.csrf();
                var existing = browser.login(IDENTIFIER, csrf);
                var absent = browser.login("inexistente-independente@example.invalid", csrf);
                assertEquals(401, existing.statusCode());
                assertEquals(401, absent.statusCode());
                assertEquals("{\"erro\":\"Credenciais inválidas.\"}", existing.body());
                assertEquals(existing.body(), absent.body());
                assertFalse(browser.session().get("autenticado").asBoolean());
            } finally {
                statement.execute("GRANT SELECT ON t06_identity.identity TO t06_identity_reader");
            }
        }
        var recovered = new Browser();
        assertEquals(204, recovered.login(IDENTIFIER, recovered.csrf()).statusCode());
        var session = recovered.session();
        assertTrue(session.get("autenticado").asBoolean());
        assertEquals(IDENTIFIER, session.get("identificador").asText());
        assertEquals(2, session.get("capacidades").size());
    }

    private static final class Browser {
        private final HttpClient client = HttpClient.newBuilder().sslContext(https.tls())
                .cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ORIGINAL_SERVER)).build();

        JsonNode session() throws Exception {
            var response = client.send(HttpRequest.newBuilder(base.resolve("/api/v1/sessao")).GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            assertEquals(200, response.statusCode());
            return JSON.readTree(response.body());
        }

        String csrf() throws Exception { return session().get("csrfToken").asText(); }

        HttpResponse<String> login(String identifier, String csrf) throws Exception {
            String body = "identificador=" + URLEncoder.encode(identifier, StandardCharsets.UTF_8)
                    + "&senha=senha-ficticia-independente";
            return client.send(HttpRequest.newBuilder(base.resolve("/api/v1/login"))
                            .header("Content-Type", "application/x-www-form-urlencoded").header("X-CSRF-TOKEN", csrf)
                            .POST(HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
        }
    }
}
