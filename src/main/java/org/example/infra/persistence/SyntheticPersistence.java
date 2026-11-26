package org.example.infra.persistence;

import org.flywaydb.core.Flyway;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.EnumerablePropertySource;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;

import javax.sql.DataSource;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.Locale;
import java.util.Map;

@Configuration(proxyBeanMethods = false)
@Profile("t05-sintetico")
class SyntheticPersistence {
    private static final Map<String, String> HTTP_SETTINGS = Map.ofEntries(
            Map.entry("server.address", "127.0.0.1"),
            Map.entry("server.port", "0"),
            Map.entry("management.server.address", "127.0.0.1"),
            Map.entry("management.server.port", "0"),
            Map.entry("management.endpoints.web.exposure.include", "health"),
            Map.entry("management.endpoints.jmx.exposure.exclude", "*"),
            Map.entry("management.endpoint.health.probes.enabled", "true"),
            Map.entry("management.endpoint.health.show-details", "never"),
            Map.entry("management.endpoint.health.group.readiness.include", "readinessState,db"));

    @Bean
    T05Target syntheticTarget(ConfigurableEnvironment environment) {
        checkSpringEnvironment(environment);
        return T05Target.from(System.getenv(), "T05_");
    }

    static void checkSpringEnvironment(ConfigurableEnvironment environment) {
        if (!Arrays.equals(environment.getActiveProfiles(), new String[]{"t05-sintetico"})
                || !"validate".equals(environment.getProperty("spring.jpa.hibernate.ddl-auto"))
                || !"never".equals(environment.getProperty("spring.sql.init.mode"))
                || !"false".equals(environment.getProperty("spring.flyway.enabled"))
                || HTTP_SETTINGS.entrySet().stream().anyMatch(
                        setting -> !setting.getValue().equals(environment.getProperty(setting.getKey())))) {
            throw new IllegalStateException("Configuração Spring incompatível com T05.");
        }
        for (var source : environment.getPropertySources()) {
            if (source instanceof EnumerablePropertySource<?> enumerable) {
                for (String name : enumerable.getPropertyNames()) {
                    String key = name.toLowerCase(Locale.ROOT).replace('_', '.');
                    if (((key.startsWith("management.") || key.startsWith("server."))
                            && HTTP_SETTINGS.keySet().stream().noneMatch(
                                    allowed -> allowed.replace('-', '.').equals(key.replace('-', '.'))))
                            || key.startsWith("spring.datasource.") || key.startsWith("spring.jpa.properties.")
                            || key.startsWith("hibernate.")
                            || (key.startsWith("spring.flyway.") && !key.equals("spring.flyway.enabled"))
                            || key.startsWith("spring.config.import") || key.startsWith("spring.config.location")
                            || key.startsWith("spring.config.additional")
                            || key.equals("spring.application.json")) {
                        throw new IllegalStateException("Propriedade incompatível com o destino T05.");
                    }
                }
            }
        }
    }

    @Bean
    DataSource dataSource(T05Target target) {
        return new AttestedDataSource(target, AttestedDataSource.APPLICATION,
                T05Target.password(System.getenv(), "T05_APPLICATION_PASSWORD"));
    }

    @Bean(initMethod = "migrate")
    Flyway syntheticFlyway(T05Target target, @Qualifier("dataSource") DataSource application)
            throws SQLException {
        DataSource migrator = new AttestedDataSource(target, AttestedDataSource.MIGRATOR,
                T05Target.password(System.getenv(), "T05_MIGRATOR_PASSWORD"));
        try (var applicationCheck = application.getConnection();
             var migrationCheck = migrator.getConnection()) {
            return flyway(migrator, "classpath:db/migration/t05");
        }
    }

    static Flyway flyway(DataSource migrator, String location) {
        return Flyway.configure()
                .dataSource(migrator)
                .defaultSchema("t05_app")
                .schemas("t05_app")
                .createSchemas(false)
                .locations(location)
                .baselineOnMigrate(false)
                .cleanDisabled(true)
                .validateOnMigrate(true)
                .outOfOrder(false)
                .load();
    }

    @Bean
    @DependsOn("syntheticFlyway")
    LocalContainerEntityManagerFactoryBean entityManagerFactory(DataSource dataSource) {
        var factory = new LocalContainerEntityManagerFactoryBean();
        factory.setDataSource(dataSource);
        factory.setPackagesToScan(Probe.class.getPackageName());
        factory.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
        factory.setJpaPropertyMap(Map.of("hibernate.hbm2ddl.auto", "validate"));
        return factory;
    }
}
