package org.example.infra.persistence;

import java.io.IOException;
import java.nio.file.Path;
import java.sql.SQLException;
import java.sql.SQLTransientConnectionException;
import java.util.Map;
import java.util.concurrent.TimeUnit;

record T05Target(String run, String container, String network, String image, int port,
                 String database, String marker) {
    static T05Target from(Map<String, String> environment, String prefix) {
        String run = field(environment, prefix + "RUN", "[0-9a-f]{32}");
        String database = field(environment, prefix + "DATABASE", "acct_t05_[0-9a-f]{32}");
        int port = Integer.parseInt(field(environment, prefix + "PORT", "[1-9][0-9]{0,4}"));
        if (!database.equals("acct_t05_" + run) || port == 5432 || port > 65535) {
            throw new IllegalStateException("Banco ou porta fora da execução sintética.");
        }
        return new T05Target(run, field(environment, prefix + "CONTAINER", "[0-9a-f]{64}"),
                field(environment, prefix + "NETWORK", "[0-9a-f]{64}"),
                field(environment, prefix + "IMAGE", "docker\\.io/library/postgres@sha256:[0-9a-f]{64}"),
                port, database, field(environment, prefix + "MARKER", "[0-9a-f]{32}"));
    }

    static String password(Map<String, String> environment, String key) {
        return field(environment, key, "[0-9a-f]{64}");
    }

    private static String field(Map<String, String> environment, String key, String pattern) {
        String value = environment.get(key);
        if (value == null || !value.matches(pattern)) {
            throw new IllegalStateException("Campo T05 ausente ou inválido: " + key);
        }
        return value;
    }

    void dockerAction(String action) throws SQLException {
        // Child process never receives database passwords and never prints Docker metadata.
        String systemRoot = System.getenv("SystemRoot");
        if (systemRoot == null) {
            throw new SQLException("T05 requer o executor Windows autorizado.");
        }
        Path powershell = Path.of(systemRoot, "System32", "WindowsPowerShell", "v1.0", "powershell.exe");
        Path script = Path.of("scripts", "t05", "pg17.ps1").toAbsolutePath();
        ProcessBuilder builder = new ProcessBuilder(powershell.toString(), "-NoProfile", "-NonInteractive",
                "-File", script.toString(), "-Action", action);
        builder.environment().keySet().removeIf(key -> key.startsWith("T05_"));
        builder.environment().putAll(Map.of(
                "T05_RUN", run, "T05_CONTAINER", container, "T05_NETWORK", network,
                "T05_IMAGE", image, "T05_PORT", Integer.toString(port)));
        builder.redirectOutput(ProcessBuilder.Redirect.DISCARD);
        builder.redirectError(ProcessBuilder.Redirect.DISCARD);
        try {
            Process process = builder.start();
            if (!process.waitFor(8, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                throw new SQLException("Tempo excedido na atestação Docker T05.");
            }
            if ("Attest".equals(action) && process.exitValue() == 42) {
                throw new SQLTransientConnectionException("PostgreSQL T05 pausado no container atestado.", "08001");
            }
            if (process.exitValue() != 0) {
                throw new SQLException("O destino Docker T05 não foi atestado.");
            }
        } catch (IOException exception) {
            throw new SQLException("Executor local da atestação Docker indisponível.", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new SQLException("Atestação Docker T05 interrompida.", exception);
        }
    }
}
