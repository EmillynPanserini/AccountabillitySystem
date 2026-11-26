package org.example.infra.persistence;

import org.example.Main;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.env.MapPropertySource;

import jakarta.persistence.EntityManagerFactory;
import javax.sql.DataSource;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SyntheticPersistenceTest {
    private static final String RUN = "a".repeat(32);

    private static Map<String, String> manifest() {
        return new HashMap<>(Map.of(
                "T05_RUN", RUN, "T05_CONTAINER", "b".repeat(64), "T05_NETWORK", "c".repeat(64),
                "T05_IMAGE", "docker.io/library/postgres@sha256:" + "d".repeat(64),
                "T05_PORT", "54321", "T05_DATABASE", "acct_t05_" + RUN, "T05_MARKER", "e".repeat(32)));
    }

    @Test
    void startsDefaultContextWithoutPersistenceEvenWithConnectionProperties() {
        try (var context = new SpringApplicationBuilder(Main.class).web(WebApplicationType.NONE)
                .run("--spring.datasource.url=jdbc:unused", "--spring.flyway.enabled=true",
                        "--spring.jpa.hibernate.ddl-auto=update")) {
            assertThat(context.getBeansOfType(DataSource.class)).isEmpty();
            assertThat(context.getBeansOfType(Flyway.class)).isEmpty();
            assertThat(context.getBeansOfType(EntityManagerFactory.class)).isEmpty();
        }
    }

    @Test
    void rejectsMissingAndMalformedManifestBeforeJdbc() {
        assertThatThrownBy(() -> T05Target.from(Map.of(), "T05_")).isInstanceOf(IllegalStateException.class);
        for (String port : new String[]{"5432", "65536", "localhost:54321", "54321,other"}) {
            var values = manifest();
            values.put("T05_PORT", port);
            assertThatThrownBy(() -> T05Target.from(values, "T05_")).isInstanceOf(IllegalStateException.class);
        }
        var values = manifest();
        values.put("T05_DATABASE", "acct_t05_" + "0".repeat(32));
        assertThatThrownBy(() -> T05Target.from(values, "T05_")).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void rejectsDockerMismatchAndAlternativeCredentialsWithoutConnecting() {
        var connections = new AtomicInteger();
        var source = new AttestedDataSource(T05Target.from(manifest(), "T05_"),
                AttestedDataSource.APPLICATION, "unused",
                (url, user, password) -> {
                    connections.incrementAndGet();
                    throw new AssertionError("Unexpected JDBC connection");
                }, () -> { throw new SQLException("Docker identity mismatch"); });
        assertThatThrownBy(source::getConnection).isInstanceOf(SQLException.class);
        assertThatThrownBy(() -> source.getConnection("other", "different")).isInstanceOf(SQLException.class);
        assertThat(connections).hasValue(0);
    }

    @Test
    void rejectsSpringDatasourceAndSchemaOverrides() {
        var properties = new HashMap<String, Object>(Map.ofEntries(
                Map.entry("spring.jpa.hibernate.ddl-auto", "validate"),
                Map.entry("spring.sql.init.mode", "never"),
                Map.entry("spring.flyway.enabled", "false"),
                Map.entry("server.address", "127.0.0.1"),
                Map.entry("server.port", "0"),
                Map.entry("management.server.address", "127.0.0.1"),
                Map.entry("management.server.port", "0"),
                Map.entry("management.endpoints.web.exposure.include", "health"),
                Map.entry("management.endpoints.jmx.exposure.exclude", "*"),
                Map.entry("management.endpoint.health.probes.enabled", "true"),
                Map.entry("management.endpoint.health.show-details", "never"),
                Map.entry("management.endpoint.health.group.readiness.include", "readinessState,db")));
        var environment = new StandardEnvironment();
        environment.getPropertySources().remove(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME);
        environment.getPropertySources().remove(StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME);
        environment.getPropertySources().addFirst(new MapPropertySource("test", properties));
        environment.setActiveProfiles("t05-sintetico");
        SyntheticPersistence.checkSpringEnvironment(environment);
        properties.put("spring.datasource.url", "unreachable");
        assertThatThrownBy(() -> SyntheticPersistence.checkSpringEnvironment(environment))
                .isInstanceOf(IllegalStateException.class);
        properties.remove("spring.datasource.url");
        for (String key : new String[]{"management.health.db.enabled",
                "management.endpoint.health.status.http-mapping.down",
                "management.endpoint.health.group.readiness.status.http-mapping.down",
                "MANAGEMENT_HEALTH_DB_ENABLED"}) {
            properties.put(key, "false");
            assertThatThrownBy(() -> SyntheticPersistence.checkSpringEnvironment(environment))
                    .isInstanceOf(IllegalStateException.class);
            properties.remove(key);
        }
        properties.put("spring.jpa.hibernate.ddl-auto", "update");
        assertThatThrownBy(() -> SyntheticPersistence.checkSpringEnvironment(environment))
                .isInstanceOf(IllegalStateException.class);
    }
}
