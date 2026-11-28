package org.example.identidade;

import org.springframework.jdbc.datasource.AbstractDataSource;

import javax.sql.DataSource;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

/** Somente o destino sintético comprovado está liberado neste incremento. */
final class SyntheticBootstrapTarget {
    private final Map<String, String> environment;
    private final String run;
    private final String database;
    private final int port;

    SyntheticBootstrapTarget(Map<String, String> environment) {
        this.environment = Map.copyOf(environment);
        run = field("T06_RUN", "[0-9a-f]{32}");
        database = field("T06_DATABASE", "acct_t06_" + run);
        port = Integer.parseInt(field("T06_PORT", "[1-9][0-9]{0,4}"));
        if (port == 5432 || port > 65535) throw new IllegalArgumentException("Porta sintética inválida.");
        field("T06_CONTAINER", "[0-9a-f]{64}");
        field("T06_NETWORK", "[0-9a-f]{64}");
        field("T06_IMAGE", "docker\\.io/library/postgres@sha256:[0-9a-f]{64}");
    }

    DataSource dataSource(boolean migration) {
        String user = migration ? "postgres" : "t06_bootstrap";
        String password = field(migration ? "T06_MIGRATION_PASSWORD" : "T06_BOOTSTRAP_PASSWORD", "[0-9a-f]{64}");
        return dataSource(user, password);
    }

    DataSource authenticationDataSource() {
        return dataSource("t06_identity_reader", field("T06_READER_PASSWORD", "[0-9a-f]{64}"));
    }

    private DataSource dataSource(String user, String password) {
        return new AbstractDataSource() {
            @Override
            public Connection getConnection() throws SQLException {
                attest();
                Properties properties = new Properties();
                properties.setProperty("user", user);
                properties.setProperty("password", password);
                properties.setProperty("connectTimeout", "5");
                properties.setProperty("socketTimeout", "15");
                Connection connection = DriverManager.getConnection("jdbc:postgresql://127.0.0.1:" + port + "/" + database, properties);
                try (var statement = connection.createStatement();
                     var result = statement.executeQuery("SELECT current_database(), current_user, current_setting('server_version_num')::int, run FROM public.t06_target")) {
                    if (!result.next() || !database.equals(result.getString(1)) || !user.equals(result.getString(2))
                            || result.getInt(3) < 170000 || result.getInt(3) >= 180000 || !run.equals(result.getString(4))
                            || result.next()) throw new SQLException("Identidade PostgreSQL T06 divergente.");
                    return connection;
                } catch (SQLException failure) {
                    connection.close();
                    throw new SQLException("Destino PostgreSQL T06 não atestado.");
                }
            }

            @Override
            public Connection getConnection(String username, String suppliedPassword) throws SQLException {
                throw new SQLException("Credenciais alternativas não permitidas.");
            }
        };
    }

    private String field(String key, String pattern) {
        String value = environment.get(key);
        if (value == null || !value.matches(pattern)) throw new IllegalArgumentException("Metadado sintético ausente ou inválido: " + key);
        return value;
    }

    private void attest() throws SQLException {
        String systemRoot = System.getenv("SystemRoot");
        if (systemRoot == null) throw new SQLException("Executor Windows T06 indisponível.");
        ProcessBuilder builder = new ProcessBuilder(
                Path.of(systemRoot, "System32", "WindowsPowerShell", "v1.0", "powershell.exe").toString(),
                "-NoProfile", "-NonInteractive", "-File", Path.of("scripts/t06/pg17.ps1").toAbsolutePath().toString(), "-Action", "Attest");
        builder.environment().keySet().removeIf(key -> key.startsWith("T06_") || key.startsWith("PG") || key.startsWith("POSTGRES_"));
        for (String name : new String[]{"RUN", "CONTAINER", "NETWORK", "IMAGE", "PORT"}) {
            builder.environment().put("T06_" + name, environment.get("T06_" + name));
        }
        builder.redirectOutput(ProcessBuilder.Redirect.DISCARD);
        builder.redirectError(ProcessBuilder.Redirect.DISCARD);
        try {
            Process process = builder.start();
            if (!process.waitFor(15, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                throw new SQLException("Atestação T06 excedeu o prazo.");
            }
            if (process.exitValue() != 0) throw new SQLException("Destino Docker T06 não atestado.");
        } catch (java.io.IOException failure) {
            throw new SQLException("Executor de atestação T06 indisponível.");
        } catch (InterruptedException failure) {
            Thread.currentThread().interrupt();
            throw new SQLException("Atestação T06 interrompida.");
        }
    }
}
