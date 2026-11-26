package org.example.identidade;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;

import javax.sql.DataSource;

/** Opt-in sintético: não migra, não executa bootstrap e não aceita destino JDBC externo. */
@Configuration(proxyBeanMethods = false)
@Profile("t06-sintetico")
public class SyntheticIdentityPersistence {
    @Bean
    DataSource identityDataSource(Environment environment) {
        if (environment.acceptsProfiles(Profiles.of("t05-sintetico"))
                || environment.containsProperty("spring.datasource.url")
                || environment.containsProperty("spring.datasource.jndi-name")) {
            throw new IllegalStateException("Perfil ou destino incompatível com identidade sintética T06.");
        }
        return new SyntheticBootstrapTarget(System.getenv()).authenticationDataSource();
    }
}
