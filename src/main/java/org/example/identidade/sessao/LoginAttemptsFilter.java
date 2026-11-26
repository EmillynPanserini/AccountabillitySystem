package org.example.identidade.sessao;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

final class LoginAttemptsFilter extends OncePerRequestFilter {
    private final LoginAttempts attempts;

    LoginAttemptsFilter(LoginAttempts attempts) { this.attempts = attempts; }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !"POST".equals(request.getMethod()) || !"/api/v1/login".equals(request.getServletPath());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (request.getQueryString() != null || request.getContentLengthLong() < 1 || request.getContentLengthLong() > 16_384
                || request.getContentType() == null || !request.getContentType().startsWith("application/x-www-form-urlencoded")) {
            SessionSecurity.error(response, 401, "Credenciais inválidas.");
            return;
        }
        String identifier = JdbcIdentityUsers.normalize(request.getParameter("identificador"));
        String password = request.getParameter("senha");
        if (identifier.isBlank() || identifier.length() > 200 || identifier.chars().anyMatch(Character::isISOControl)
                || password == null || password.isEmpty() || password.length() > 1024) {
            SessionSecurity.error(response, 401, "Credenciais inválidas.");
            return;
        }
        // Usa o endereço do socket; forwarded headers não constituem identidade confiável de origem.
        var reservation = attempts.reserve(identifier, request.getRemoteAddr());
        if (reservation == null) {
            response.setHeader("Retry-After", "900");
            SessionSecurity.error(response, 429, "Tentativas temporariamente limitadas.");
            return;
        }
        try {
            chain.doFilter(request, response);
        } finally {
            attempts.finish(reservation, response.getStatus() != 204);
        }
    }
}
