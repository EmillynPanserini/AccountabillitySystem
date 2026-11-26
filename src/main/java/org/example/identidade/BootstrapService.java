package org.example.identidade;

import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;

import javax.sql.DataSource;
import java.nio.CharBuffer;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.Objects;
import java.util.UUID;

public final class BootstrapService {
    private final DataSource dataSource;
    private final Argon2PasswordEncoder passwords = passwordEncoder();

    public BootstrapService(DataSource dataSource) {
        this.dataSource = Objects.requireNonNull(dataSource);
    }

    static Argon2PasswordEncoder passwordEncoder() {
        return IdentityPasswords.encoder();
    }

    /** Consome e apaga o buffer da senha, inclusive quando a operação é recusada. */
    public UUID createAdministrator(BootstrapRequest request, char[] password) {
        try {
            Objects.requireNonNull(request, "Verificação do operador e destinatário obrigatória.");
            if (password == null || password.length == 0 || password.length > 1024) {
                throw new IllegalArgumentException("Senha ausente ou excede o limite de entrada.");
            }
            String hash = passwords.encode(CharBuffer.wrap(password));
            return persist(request, hash);
        } finally {
            if (password != null) Arrays.fill(password, '\0');
        }
    }

    private UUID persist(BootstrapRequest request, String hash) {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                lockOpenBootstrap(connection);
                UUID id = UUID.randomUUID();
                execute(connection, "INSERT INTO t06_identity.identity (id, login, password_hash, initial_admin) VALUES (?, ?, ?, true)",
                        id, request.recipient(), hash);
                execute(connection, "INSERT INTO t06_identity.capability (identity_id, name) VALUES (?, 'ADMINISTRAR_IDENTIDADES'), (?, 'CRIAR_EMPRESA')", id, id);
                execute(connection, "INSERT INTO t06_identity.audit (administrator_id, operator, recipient, verification_reference) VALUES (?, ?, ?, ?)",
                        id, request.operator(), request.recipient(), request.verificationReference());
                execute(connection, "UPDATE t06_identity.bootstrap SET administrator_id = ?, closed_at = CURRENT_TIMESTAMP WHERE singleton = true", id);
                connection.commit();
                return id;
            } catch (SQLException | RuntimeException failure) {
                connection.rollback();
                throw failure;
            }
        } catch (SQLException failure) {
            // Erros do driver podem conter valores de parâmetros; não os propagar para o operador/log.
            throw new IllegalStateException("Bootstrap não concluído; transação revertida ou resultado de commit indeterminado.");
        }
    }

    private void lockOpenBootstrap(Connection connection) throws SQLException {
        try (var statement = connection.prepareStatement("SELECT closed_at FROM t06_identity.bootstrap WHERE singleton = true FOR UPDATE");
             var result = statement.executeQuery()) {
            if (!result.next() || result.getObject(1) != null) {
                throw new IllegalStateException("Bootstrap encerrado ou não provisionado.");
            }
        }
    }

    private void execute(Connection connection, String sql, Object... values) throws SQLException {
        try (var statement = connection.prepareStatement(sql)) {
            for (int index = 0; index < values.length; index++) statement.setObject(index + 1, values[index]);
            if (statement.executeUpdate() == 0) throw new SQLException("Operação de bootstrap sem efeito.");
        }
    }
}
