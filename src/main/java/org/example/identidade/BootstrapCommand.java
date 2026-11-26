package org.example.identidade;

import java.io.Console;
import java.util.Arrays;

/** Entrada explícita; não é bean Spring nem executa durante Main/startup. */
public final class BootstrapCommand {
    private BootstrapCommand() {}

    public static void main(String[] args) {
        if (!Arrays.equals(args, new String[]{"--bootstrap-sintetico"})) {
            throw new IllegalArgumentException("Exige --bootstrap-sintetico e autorização operacional específica.");
        }
        Console console = System.console();
        if (console == null) throw new IllegalStateException("Console local obrigatório para entrada protegida.");
        var target = new SyntheticBootstrapTarget(System.getenv());
        String operator = console.readLine("Operador previamente verificado: ");
        String recipient = console.readLine("Identificador do destinatário previamente verificado: ");
        String reference = console.readLine("Referência não sigilosa da verificação e autorização: ");
        if (!"VERIFICADOS".equals(console.readLine("Confirme operador/destinatário verificados digitando VERIFICADOS: "))) {
            throw new IllegalArgumentException("Verificação não confirmada.");
        }
        var request = new BootstrapRequest(operator, recipient, reference);
        var service = new BootstrapService(target.dataSource(false));
        char[] password = console.readPassword("Senha inicial (não exibida): ");
        service.createAdministrator(request, password);
        console.printf("Administrador inicial criado; bootstrap definitivamente encerrado.%n");
    }
}
