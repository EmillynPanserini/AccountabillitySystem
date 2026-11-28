package org.example.identidade;

import java.util.Locale;

/** A referência registra a verificação externa feita pelo operador, não uma autenticação web. */
public record BootstrapRequest(String operator, String recipient, String verificationReference) {
    public BootstrapRequest {
        operator = required(operator, 200);
        recipient = required(recipient, 200).toLowerCase(Locale.ROOT);
        verificationReference = required(verificationReference, 300);
    }

    private static String required(String value, int maximum) {
        if (value == null || value.isBlank() || value.length() > maximum
                || value.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("Identificação ou referência de verificação inválida.");
        }
        return value.strip();
    }
}
