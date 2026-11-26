package org.example.identidade;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class BootstrapPersistenceIT {
    private static DataSource migrator;
    private static DataSource application;
    private static Flyway flyway;
    private static final BootstrapRequest REQUEST = new BootstrapRequest("operador-ficticio", "admin-ficticio@example.invalid", "autorizacao-sintetica-t06");

    @BeforeAll
    static void migrateAttestedTarget() {
        var target = new SyntheticBootstrapTarget(System.getenv());
        migrator = target.dataSource(true);
        application = target.dataSource(false);
        flyway = Flyway.configure().dataSource(migrator).locations("classpath:db/migration/t06")
                .defaultSchema("t06_history").schemas("t06_history")
                .cleanDisabled(true).baselineOnMigrate(false).outOfOrder(false).load();
        flyway.migrate();
    }

    @BeforeEach
    void resetSyntheticFixture() throws SQLException {
        // Somente o proprietário da fixture atestada pode resetar os cenários; o comando não tem esses privilégios.
        sql(migrator, "TRUNCATE t06_identity.audit, t06_identity.capability, t06_identity.bootstrap, t06_identity.identity");
        sql(migrator, "INSERT INTO t06_identity.bootstrap(singleton) VALUES (true)");
    }

    @Test
    void createsAdministratorCapabilitiesAuditAndClosesAtomically() throws SQLException {
        char[] password = password();
        var id = new BootstrapService(application).createAdministrator(REQUEST, password);
        assertArrayEquals(new char[password.length], password);
        try (var connection = application.getConnection(); var statement = connection.createStatement();
             var result = statement.executeQuery("SELECT i.id, i.login, i.password_hash, b.administrator_id, b.closed_at, a.operator, a.recipient, a.verification_reference, a.occurred_at FROM t06_identity.identity i JOIN t06_identity.bootstrap b ON b.administrator_id=i.id JOIN t06_identity.audit a ON a.administrator_id=i.id")) {
            assertTrue(result.next());
            assertEquals(id, result.getObject(1));
            assertEquals(REQUEST.recipient(), result.getString(2));
            assertTrue(BootstrapService.passwordEncoder().matches("ficticia-exclusiva-t06", result.getString(3)));
            assertEquals(id, result.getObject(4));
            assertNotNull(result.getObject(5));
            assertEquals(REQUEST.operator(), result.getString(6));
            assertEquals(REQUEST.recipient(), result.getString(7));
            assertEquals(REQUEST.verificationReference(), result.getString(8));
            assertEquals(result.getObject(5), result.getObject(9));
            assertFalse(result.next());
        }
        assertEquals(List.of("ADMINISTRAR_IDENTIDADES", "CRIAR_EMPRESA"), capabilities());
        assertThrows(IllegalStateException.class, () -> new BootstrapService(application).createAdministrator(REQUEST, password()));
        assertEquals(1, count("identity"));
        assertEquals(1, count("audit"));
    }

    @Test
    void concurrentExecutionsCreateExactlyOneAdministrator() throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var attempts = new ArrayList<java.util.concurrent.Future<Boolean>>();
            for (int index = 0; index < 2; index++) {
                int recipient = index;
                attempts.add(executor.submit(() -> {
                    assertTrue(start.await(10, TimeUnit.SECONDS));
                    try {
                        new BootstrapService(application).createAdministrator(new BootstrapRequest("operador-ficticio", "admin-" + recipient, "verificacao-ficticia"), password());
                        return true;
                    } catch (IllegalStateException closed) {
                        assertEquals("Bootstrap encerrado ou não provisionado.", closed.getMessage());
                        return false;
                    }
                }));
            }
            start.countDown();
            int successes = 0;
            for (var attempt : attempts) if (attempt.get(40, TimeUnit.SECONDS)) successes++;
            assertEquals(1, successes);
        }
        assertEquals(1, count("identity"));
        assertEquals(1, count("audit"));
        assertEquals(2, count("capability"));
    }

    @Test
    void failureAtClosureRollsBackIdentityCapabilitiesAndAudit() throws SQLException {
        sql(migrator, "CREATE FUNCTION t06_identity.fail_test_closure() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN RAISE EXCEPTION 'falha sintetica'; END; $$");
        sql(migrator, "CREATE TRIGGER test_failure BEFORE UPDATE ON t06_identity.bootstrap FOR EACH ROW EXECUTE FUNCTION t06_identity.fail_test_closure()");
        try {
            assertThrows(IllegalStateException.class, () -> new BootstrapService(application).createAdministrator(REQUEST, password()));
            assertEquals(0, count("identity"));
            assertEquals(0, count("audit"));
            assertEquals(0, count("capability"));
            try (var connection = application.getConnection(); var statement = connection.createStatement();
                 var result = statement.executeQuery("SELECT administrator_id, closed_at FROM t06_identity.bootstrap")) {
                assertTrue(result.next());
                assertNull(result.getObject(1));
                assertNull(result.getObject(2));
            }
        } finally {
            sql(migrator, "DROP TRIGGER test_failure ON t06_identity.bootstrap");
            sql(migrator, "DROP FUNCTION t06_identity.fail_test_closure()");
        }
        assertNotNull(new BootstrapService(application).createAdministrator(REQUEST, password()));
    }

    @Test
    void postgresEnforcesUniquenessAndPreventsReopeningOrAuditTampering() throws SQLException {
        new BootstrapService(application).createAdministrator(REQUEST, password());
        assertEquals("23505", assertThrows(SQLException.class, () -> sql(application,
                "INSERT INTO t06_identity.identity(id, login, password_hash, initial_admin) SELECT gen_random_uuid(), 'other-fictitious', password_hash, true FROM t06_identity.identity")).getSQLState());
        assertEquals("23505", assertThrows(SQLException.class, () -> sql(application,
                "INSERT INTO t06_identity.identity(id, login, password_hash) SELECT gen_random_uuid(), login, password_hash FROM t06_identity.identity")).getSQLState());
        assertThrows(SQLException.class, () -> sql(application, "UPDATE t06_identity.bootstrap SET closed_at=NULL, administrator_id=NULL"));
        assertEquals("42501", assertThrows(SQLException.class, () -> sql(application, "DELETE FROM t06_identity.audit")).getSQLState());
        assertEquals("42501", assertThrows(SQLException.class, () -> sql(application, "DELETE FROM t06_identity.bootstrap")).getSQLState());
        assertEquals(1, count("audit"));
    }

    @Test
    void migrationReexecutionPreservesClosedState() throws SQLException {
        new BootstrapService(application).createAdministrator(REQUEST, password());
        assertEquals(0, flyway.migrate().migrationsExecuted);
        flyway.validate();
        assertThrows(IllegalStateException.class, () -> new BootstrapService(application).createAdministrator(REQUEST, password()));
        assertEquals(1, count("identity"));
    }

    private static char[] password() { return "ficticia-exclusiva-t06".toCharArray(); }

    private static void sql(DataSource source, String sql) throws SQLException {
        try (var connection = source.getConnection(); var statement = connection.createStatement()) { statement.execute(sql); }
    }

    private int count(String table) throws SQLException {
        try (var connection = application.getConnection(); var statement = connection.createStatement();
             var result = statement.executeQuery("SELECT count(*) FROM t06_identity." + table)) {
            assertTrue(result.next());
            return result.getInt(1);
        }
    }

    private List<String> capabilities() throws SQLException {
        var result = new ArrayList<String>();
        try (var connection = application.getConnection(); var statement = connection.createStatement();
             var rows = statement.executeQuery("SELECT name FROM t06_identity.capability ORDER BY name")) {
            while (rows.next()) result.add(rows.getString(1));
        }
        return result;
    }
}
