package org.example.identidade;

import org.example.Main;
import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;

import javax.sql.DataSource;
import java.nio.CharBuffer;
import java.util.Base64;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class BootstrapServiceTest {
    @Test
    void usesApprovedArgon2idParametersAndIndependentSalts() {
        var encoder = BootstrapService.passwordEncoder();
        char[] password = "ficticia-exclusiva-do-teste".toCharArray();
        String first = encoder.encode(CharBuffer.wrap(password));
        String second = encoder.encode(CharBuffer.wrap(password));
        assertTrue(first.startsWith("$argon2id$v=19$m=19456,t=2,p=1$"));
        String[] parts = first.split("\\$");
        assertEquals(16, Base64.getDecoder().decode(parts[4]).length);
        assertEquals(32, Base64.getDecoder().decode(parts[5]).length);
        assertNotEquals(first, second);
        assertTrue(encoder.matches(CharBuffer.wrap(password), first));
        assertFalse(encoder.matches("outra-senha-ficticia", first));
    }

    @Test
    void rejectsInvalidInputsWithoutConnectingAndErasesPassword() {
        DataSource source = mock(DataSource.class);
        char[] password = "ficticia".toCharArray();
        assertThrows(NullPointerException.class, () -> new BootstrapService(source).createAdministrator(null, password));
        assertArrayEquals(new char[password.length], password);
        assertThrows(IllegalArgumentException.class, () -> new BootstrapRequest("", "destinatario", "referencia"));
        assertThrows(IllegalArgumentException.class, () -> new BootstrapRequest("operador", "destinatario", "\n"));
        assertThrows(IllegalArgumentException.class, () -> new SyntheticBootstrapTarget(Map.of()));
        verifyNoInteractions(source);
    }

    @Test
    void defaultStartupDoesNotRegisterBootstrapOrDatasource() {
        try (var context = new SpringApplicationBuilder(Main.class).web(WebApplicationType.NONE)
                .properties("spring.main.banner-mode=off", "logging.level.root=ERROR").run()) {
            assertTrue(context.getBeansOfType(BootstrapService.class).isEmpty());
            assertTrue(context.getBeansOfType(DataSource.class).isEmpty());
        }
    }

    @Test
    void commandRequiresExplicitModeBeforeAnyConnection() {
        assertThrows(IllegalArgumentException.class, () -> BootstrapCommand.main(new String[0]));
        assertThrows(IllegalArgumentException.class, () -> BootstrapCommand.main(new String[]{"--password=forbidden"}));
    }
}
