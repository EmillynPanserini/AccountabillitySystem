package org.example.infra.persistence;

import org.springframework.jdbc.datasource.AbstractDataSource;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

final class AttestedDataSource extends AbstractDataSource {
    static final String MIGRATOR = "t05_migrator";
    static final String APPLICATION = "t05_application";

    @FunctionalInterface
    interface Connector {
        Connection connect(String url, String username, String password) throws SQLException;
    }

    @FunctionalInterface
    interface DockerAttestation {
        void check() throws SQLException;
    }

    private final T05Target target;
    private final String role;
    private final String password;
    private final Connector connector;
    private final DockerAttestation attestation;

    AttestedDataSource(T05Target target, String role, String password) {
        this(target, role, password, DriverManager::getConnection, () -> target.dockerAction("Attest"));
    }

    AttestedDataSource(T05Target target, String role, String password, Connector connector,
                       DockerAttestation attestation) {
        if (!MIGRATOR.equals(role) && !APPLICATION.equals(role)) {
            throw new IllegalArgumentException("Papel fora da execução T05.");
        }
        this.target = target;
        this.role = role;
        this.password = password;
        this.connector = connector;
        this.attestation = attestation;
    }

    @Override
    public Connection getConnection() throws SQLException {
        attestation.check();
        String url = "jdbc:postgresql://127.0.0.1:" + target.port() + "/" + target.database()
                + "?connectTimeout=2&loginTimeout=3&socketTimeout=3&sslmode=disable";
        Connection connection = connector.connect(url, role, password);
        try {
            connection.setReadOnly(true);
            connection.setAutoCommit(false);
            try (var statement = connection.createStatement();
                 var result = statement.executeQuery("""
                         SELECT current_database(), session_user, current_user,
                                current_setting('server_version_num')::integer / 10000,
                                inet_server_port(), marker
                         FROM t05_control.run_identity
                         """)) {
                if (!result.next() || !target.database().equals(result.getString(1))
                        || !role.equals(result.getString(2)) || !role.equals(result.getString(3))
                        || result.getInt(4) != 17 || result.getInt(5) != 5432
                        || !target.marker().equals(result.getString(6)) || result.next()) {
                    throw new SQLException("Identidade PostgreSQL T05 divergente.");
                }
            }
            connection.rollback();
            connection.setAutoCommit(true);
            connection.setReadOnly(false);
            return connection;
        } catch (SQLException | RuntimeException exception) {
            try {
                connection.close();
            } catch (SQLException closeFailure) {
                exception.addSuppressed(closeFailure);
            }
            throw exception;
        }
    }

    @Override
    public Connection getConnection(String username, String password) throws SQLException {
        throw new SQLException("Credenciais alternativas não são aceitas pelo datasource T05.");
    }
}
