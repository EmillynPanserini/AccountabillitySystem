package org.example.identidade;

import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class BootstrapIndependentTest {
    private static final BootstrapRequest REQUEST = new BootstrapRequest("operador-ficticio", "admin@example.invalid", "verificacao-ficticia");

    @Test
    void oversizedPasswordIsErasedWithoutAccessingDatabase() {
        var source = mock(DataSource.class);
        char[] password = "x".repeat(1025).toCharArray();
        assertThrows(IllegalArgumentException.class, () -> new BootstrapService(source).createAdministrator(REQUEST, password));
        assertArrayEquals(new char[1025], password);
        verifyNoInteractions(source);
    }

    @Test
    void jdbcConnectionFailureDoesNotExposeDriverDetailsAndErasesPassword() throws SQLException {
        var source = mock(DataSource.class);
        when(source.getConnection()).thenThrow(new SQLException("detalhe-sintetico-que-nao-deve-vazar"));
        char[] password = "senha-ficticia".toCharArray();
        var failure = assertThrows(IllegalStateException.class,
                () -> new BootstrapService(source).createAdministrator(REQUEST, password));
        assertFalse(failure.getMessage().contains("detalhe-sintetico"));
        assertNull(failure.getCause());
        assertEquals(0, failure.getSuppressed().length);
        assertArrayEquals(new char[password.length], password);
    }

    @Test
    void insertFailureRollsBackInsteadOfCommittingAndClosesConnection() throws SQLException {
        var source = mock(DataSource.class);
        var connection = mock(Connection.class);
        var statement = mock(PreparedStatement.class);
        var rows = mock(ResultSet.class);
        when(source.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(anyString())).thenReturn(statement);
        when(statement.executeQuery()).thenReturn(rows);
        when(rows.next()).thenReturn(true);
        when(rows.getObject(1)).thenReturn(null);
        when(statement.executeUpdate()).thenThrow(new SQLException("falha-sintetica"));
        char[] password = "senha-ficticia".toCharArray();

        assertThrows(IllegalStateException.class,
                () -> new BootstrapService(source).createAdministrator(REQUEST, password));

        var transaction = inOrder(connection);
        transaction.verify(connection).setAutoCommit(false);
        transaction.verify(connection).rollback();
        transaction.verify(connection).close();
        verify(connection, never()).commit();
        assertArrayEquals(new char[password.length], password);
    }

    @Test
    void mismatchedDatabaseOrUnsafePortIsRejectedBeforeAttestation() {
        for (String port : new String[]{"5432", "0", "65536", "-1", "127.0.0.1:5432"}) {
            var metadata = metadata();
            metadata.put("T06_PORT", port);
            assertThrows(IllegalArgumentException.class, () -> new SyntheticBootstrapTarget(metadata));
        }
        var metadata = metadata();
        metadata.put("T06_DATABASE", "existing_database");
        assertThrows(IllegalArgumentException.class, () -> new SyntheticBootstrapTarget(metadata));
    }

    @Test
    void alternativeCredentialsAreRejectedWithoutOpeningConnection() {
        var source = new SyntheticBootstrapTarget(metadata()).dataSource(false);
        assertThrows(SQLException.class, () -> source.getConnection("other-user", "fictitious"));
    }

    private Map<String, String> metadata() {
        String run = "a".repeat(32);
        return new HashMap<>(Map.of(
                "T06_RUN", run,
                "T06_DATABASE", "acct_t06_" + run,
                "T06_PORT", "55432",
                "T06_CONTAINER", "b".repeat(64),
                "T06_NETWORK", "c".repeat(64),
                "T06_IMAGE", "docker.io/library/postgres@sha256:" + "d".repeat(64),
                "T06_BOOTSTRAP_PASSWORD", "e".repeat(64)));
    }
}
