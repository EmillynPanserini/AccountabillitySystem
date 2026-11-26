package org.example.infra.persistence;

import org.example.Main;
import org.flywaydb.core.api.FlywayException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.BeanCreationException;
import org.springframework.boot.builder.SpringApplicationBuilder;

import java.net.SocketTimeoutException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SyntheticPersistenceIT {
    private static final String MIGRATION_PATH = "classpath:db/migration/t05";
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();

    @Test
    void migratesOnceValidatesPermissionsAndRecoversReadiness() throws Exception {
        var target = T05Target.from(System.getenv(), "T05_");
        var migrator = dataSource("T05_", AttestedDataSource.MIGRATOR);
        assertThat(scalar(migrator, "SELECT count(*) FROM information_schema.tables WHERE table_schema='t05_app'"))
                .isEqualTo("0");
        try (var application = start()) {
            var flyway = SyntheticPersistence.flyway(migrator, MIGRATION_PATH);
            assertThat(flyway.info().applied()).hasSize(1);
            assertThat(flyway.migrate().migrationsExecuted).isZero();
            flyway.validate();

            var app = dataSource("T05_", AttestedDataSource.APPLICATION);
            assertThat(scalar(app, "SELECT count(*) FROM t05_app.probe")).isEqualTo("0");
            denied(app, "CREATE TABLE t05_app.forbidden (id bigint)");
            denied(app, "ALTER TABLE t05_app.probe DROP COLUMN descricao");
            denied(app, "CREATE TEMP TABLE forbidden (id bigint)");
            denied(app, "DELETE FROM t05_app.flyway_schema_history");
            denied(app, "UPDATE t05_control.run_identity SET marker='forbidden'");

            int serverPort = application.getEnvironment().getRequiredProperty("local.server.port", Integer.class);
            int managementPort = application.getEnvironment().getRequiredProperty("local.management.port", Integer.class);
            assertThat(serverPort).isNotEqualTo(managementPort);
            assertThat(get(managementPort, "/actuator/health/readiness").statusCode()).isEqualTo(200);
            target.dockerAction("Pause");
            try {
                assertThat(get(managementPort, "/actuator/health/readiness").statusCode()).isEqualTo(503);
                var status = get(serverPort, "/api/status");
                assertThat(status.statusCode()).isEqualTo(200);
                assertThat(status.headers().firstValue("Content-Type")).hasValueSatisfying(
                        value -> assertThat(value).startsWith("text/plain"));
                assertThat(status.body()).isEqualTo("AccountabilitySystem funcionando!");
                assertThatThrownBy(() -> { try (var disabled = start()) { } })
                        .satisfies(SyntheticPersistenceIT::assertDatabaseStartupFailure);
            } finally {
                target.dockerAction("Resume");
            }
            assertThat(get(managementPort, "/actuator/health/readiness").statusCode()).isEqualTo(200);
        }
    }

    @Test
    void rejectsDivergentChecksumAndPhysicalSchemaOnDedicatedTarget(@TempDir Path evidence) throws Exception {
        var migrator = dataSource("T05_DRIFT_", AttestedDataSource.MIGRATOR);
        var flyway = SyntheticPersistence.flyway(migrator, MIGRATION_PATH);
        assertThat(flyway.migrate().migrationsExecuted).isEqualTo(1);
        assertThatThrownBy(() -> SyntheticPersistence.flyway(migrator, "classpath:t05/checksum").validate())
                .isInstanceOf(FlywayException.class).hasMessageContaining("checksum");
        try (var connection = migrator.getConnection(); var statement = connection.createStatement()) {
            statement.execute("ALTER TABLE t05_app.probe DROP COLUMN descricao");
        }
        flyway.validate();
        var startupLog = evidence.resolve("startup.log");
        var javaExecutable = Path.of(System.getProperty("java.home"), "bin", "java.exe");
        var jar = Path.of("target", "accountabilitySystem-1.0-SNAPSHOT.jar");
        var startup = new ProcessBuilder(javaExecutable.toString(), "-jar", jar.toString(),
                "--spring.profiles.active=t05-sintetico").redirectErrorStream(true)
                .redirectOutput(startupLog.toFile());
        var environment = startup.environment();
        environment.keySet().removeIf(key -> key.startsWith("T05_"));
        System.getenv().forEach((key, value) -> {
            if (key.startsWith("T05_DRIFT_")) {
                environment.put("T05_" + key.substring("T05_DRIFT_".length()), value);
            }
        });
        var process = startup.start();
        try {
            assertThat(process.waitFor(90, TimeUnit.SECONDS))
                    .as("startup do JAR com esquema divergente deve terminar").isTrue();
            assertThat(process.exitValue()).isNotZero();
            assertThat(Files.readString(startupLog).contains("missing column [descricao]"))
                    .as("startup deve falhar na validação Hibernate da coluna removida").isTrue();
        } finally {
            if (process.isAlive()) {
                process.destroyForcibly();
            }
        }
    }

    @Test
    void rollsBackFailedTransactionalMigrationOnDedicatedTarget() throws Exception {
        var migrator = dataSource("T05_ROLLBACK_", AttestedDataSource.MIGRATOR);
        var flyway = SyntheticPersistence.flyway(migrator, MIGRATION_PATH);
        assertThat(flyway.migrate().migrationsExecuted).isEqualTo(1);
        var failing = FlywayFixture.withFailure(migrator);
        assertThatThrownBy(failing::migrate).isInstanceOf(FlywayException.class);
        assertThat(scalar(migrator, "SELECT count(*) FROM t05_app.flyway_schema_history WHERE version='2'"))
                .isEqualTo("0");
        assertThat(scalar(migrator, "SELECT CASE WHEN to_regclass('t05_app.rollback_probe') IS NULL "
                + "THEN 'absent' ELSE 'present' END")).isEqualTo("absent");
        flyway.validate();
    }

    private static org.springframework.context.ConfigurableApplicationContext start() {
        return new SpringApplicationBuilder(Main.class).profiles("t05-sintetico").run();
    }

    private static void assertDatabaseStartupFailure(Throwable error) {
        boolean migrationInitializationFailed = false;
        boolean databaseUnavailable = false;
        for (Throwable cause = error; cause != null; cause = cause.getCause()) {
            if (cause instanceof BeanCreationException beanFailure
                    && "syntheticFlyway".equals(beanFailure.getBeanName())) {
                migrationInitializationFailed = true;
            }
            if (cause instanceof SocketTimeoutException
                    || cause instanceof SQLException sql && sql.getSQLState() != null
                    && sql.getSQLState().startsWith("08")) {
                databaseUnavailable = true;
            }
        }
        assertThat(migrationInitializationFailed).as("Flyway startup was blocked").isTrue();
        assertThat(databaseUnavailable).as("PostgreSQL connection failure or timeout").isTrue();
    }

    private static AttestedDataSource dataSource(String prefix, String role) {
        String passwordKey = AttestedDataSource.APPLICATION.equals(role) ? "APPLICATION_PASSWORD" : "MIGRATOR_PASSWORD";
        return new AttestedDataSource(T05Target.from(System.getenv(), prefix), role,
                T05Target.password(System.getenv(), prefix + passwordKey));
    }

    private static String scalar(AttestedDataSource source, String sql) throws SQLException {
        try (var connection = source.getConnection(); var statement = connection.createStatement();
             var result = statement.executeQuery(sql)) {
            assertThat(result.next()).isTrue();
            return result.getString(1);
        }
    }

    private static void denied(AttestedDataSource source, String sql) throws SQLException {
        try (var connection = source.getConnection(); var statement = connection.createStatement()) {
            assertThatThrownBy(() -> statement.execute(sql)).isInstanceOfSatisfying(SQLException.class,
                    exception -> assertThat(exception.getSQLState()).isEqualTo("42501"));
        }
    }

    private HttpResponse<String> get(int port, String path) throws Exception {
        return http.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                .timeout(Duration.ofSeconds(20)).build(), HttpResponse.BodyHandlers.ofString());
    }

    private static class FlywayFixture {
        static org.flywaydb.core.Flyway withFailure(AttestedDataSource source) {
            return org.flywaydb.core.Flyway.configure().dataSource(source)
                    .schemas("t05_app").defaultSchema("t05_app").createSchemas(false)
                    .locations(MIGRATION_PATH, "classpath:t05/rollback").baselineOnMigrate(false)
                    .cleanDisabled(true).validateOnMigrate(true).load();
        }
    }
}
