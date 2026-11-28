package org.example.identidade;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertThrows;

class SyntheticIdentityPersistenceTest {
    @Test
    void rejectsT05AndExternalDatasourceConfigurationBeforeReadingTarget() {
        var configuration = new SyntheticIdentityPersistence();
        var t05 = new MockEnvironment();
        t05.setActiveProfiles("t05-sintetico", "t06-sintetico");
        assertThrows(IllegalStateException.class, () -> configuration.identityDataSource(t05));
        assertThrows(IllegalStateException.class, () -> configuration.identityDataSource(
                new MockEnvironment().withProperty("spring.datasource.url", "jdbc:unreachable")));
        assertThrows(IllegalStateException.class, () -> configuration.identityDataSource(
                new MockEnvironment().withProperty("spring.datasource.jndi-name", "unreachable")));
    }

    @Test
    void requiresSyntheticTargetMetadataWithoutFallback() {
        assertThrows(IllegalArgumentException.class, () -> new SyntheticBootstrapTarget(Map.of()));
    }
}
