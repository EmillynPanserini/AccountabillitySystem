package org.example.identidade.sessao;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class SessionController {
    @GetMapping("/api/v1/sessao")
    public ResponseEntity<SessionView> session(Authentication authentication, CsrfToken csrf) {
        boolean authenticated = authentication != null && authentication.isAuthenticated()
                && !(authentication instanceof AnonymousAuthenticationToken);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(new SessionView(
                authenticated, authenticated ? authentication.getName() : null,
                authenticated ? authentication.getAuthorities().stream().map(Object::toString).sorted().toList() : List.of(),
                csrf.getToken(), csrf.getHeaderName()));
    }

    public record SessionView(boolean autenticado, String identificador, List<String> capacidades,
                              String csrfToken, String csrfHeader) {}
}
