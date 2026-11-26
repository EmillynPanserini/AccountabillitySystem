package org.example.identidade;

import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;

public final class IdentityPasswords {
    private IdentityPasswords() {}

    public static Argon2PasswordEncoder encoder() {
        return new Argon2PasswordEncoder(16, 32, 1, 19456, 2);
    }
}
