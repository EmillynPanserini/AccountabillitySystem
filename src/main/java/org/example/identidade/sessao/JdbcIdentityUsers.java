package org.example.identidade.sessao;

import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import javax.sql.DataSource;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Locale;

/** Consulta preparada para a persistência T06; não cria nem conecta um datasource no startup. */
public final class JdbcIdentityUsers implements UserDetailsService {
    private final DataSource dataSource;

    public JdbcIdentityUsers(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        if (dataSource == null) throw unavailable();
        String identifier = normalize(username);
        try (var connection = dataSource.getConnection();
             var query = connection.prepareStatement("SELECT i.login, i.password_hash, c.name FROM t06_identity.identity i LEFT JOIN t06_identity.capability c ON c.identity_id = i.id WHERE i.login = ?")) {
            query.setString(1, identifier);
            try (var rows = query.executeQuery()) {
                if (!rows.next()) throw unavailable();
                String login = rows.getString(1);
                String hash = rows.getString(2);
                var capabilities = new ArrayList<String>();
                do {
                    String capability = rows.getString(3);
                    if (capability != null) capabilities.add(capability);
                } while (rows.next());
                return User.withUsername(login).password(hash).authorities(capabilities.toArray(String[]::new)).build();
            }
        } catch (SQLException failure) {
            // Não propagar detalhes JDBC, parâmetros ou existência da identidade para a resposta/log.
            throw unavailable();
        }
    }

    static String normalize(String username) {
        return username == null ? "" : username.strip().toLowerCase(Locale.ROOT);
    }

    private UsernameNotFoundException unavailable() {
        return new UsernameNotFoundException("Credenciais inválidas.");
    }
}
