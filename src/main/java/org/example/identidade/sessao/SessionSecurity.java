package org.example.identidade.sessao;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import org.springframework.boot.web.servlet.server.Session.SessionTrackingMode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.example.identidade.IdentityPasswords;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.boot.web.servlet.server.ConfigurableServletWebServerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.security.web.csrf.HttpSessionCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRequestHandler;
import org.springframework.security.web.csrf.XorCsrfTokenRequestAttributeHandler;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.sql.DataSource;
import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.util.Set;

@Configuration(proxyBeanMethods = false)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class SessionSecurity {
    @Bean
    UserDetailsService identityUsers(ObjectProvider<DataSource> sources, Environment environment) {
        // T05 é uma fundação técnica e nunca um destino de autenticação.
        return new JdbcIdentityUsers(environment.acceptsProfiles(Profiles.of("t05-sintetico")) ? null : sources.getIfAvailable());
    }

    @Bean
    LoginAttempts loginAttempts() { return new LoginAttempts(Clock.systemUTC()); }

    @Bean
    WebServerFactoryCustomizer<ConfigurableServletWebServerFactory> sessionCookies() {
        return factory -> {
            var session = new org.springframework.boot.web.servlet.server.Session();
            session.setTimeout(Duration.ofMinutes(30));
            session.setTrackingModes(Set.of(SessionTrackingMode.COOKIE));
            session.getCookie().setName("JSESSIONID");
            session.getCookie().setHttpOnly(true);
            session.getCookie().setSecure(true);
            session.getCookie().setSameSite(org.springframework.boot.web.server.Cookie.SameSite.LAX);
            factory.setSession(session);
        };
    }

    @Bean
    SecurityFilterChain sessionFilterChain(HttpSecurity http, UserDetailsService users, LoginAttempts attempts) throws Exception {
        var provider = new DaoAuthenticationProvider(users);
        provider.setPasswordEncoder(IdentityPasswords.encoder());
        var tokens = new HttpSessionCsrfTokenRepository();
        tokens.setHeaderName("X-CSRF-TOKEN");
        // Um único provedor evita fallback para encoder/autenticação autoconfigurados após senha incorreta.
        http.authenticationManager(new ProviderManager(provider))
                .httpBasic(AbstractHttpConfigurer::disable)
                .requestCache(AbstractHttpConfigurer::disable)
                .csrf(csrf -> csrf.csrfTokenRepository(tokens).csrfTokenRequestHandler(new CsrfTokenRequestHandler() {
                    private final XorCsrfTokenRequestAttributeHandler delegate = new XorCsrfTokenRequestAttributeHandler();
                    @Override
                    public void handle(HttpServletRequest request, HttpServletResponse response, java.util.function.Supplier<CsrfToken> token) {
                        delegate.handle(request, response, token);
                    }
                    @Override
                    public String resolveCsrfTokenValue(HttpServletRequest request, CsrfToken token) {
                        return request.getHeader(token.getHeaderName()) == null ? null : delegate.resolveCsrfTokenValue(request, token);
                    }
                }))
                .sessionManagement(session -> session.sessionFixation(fixation -> fixation.changeSessionId()))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(org.springframework.http.HttpMethod.GET, "/api/status", "/actuator/health/readiness", "/api/v1/sessao").permitAll()
                        .requestMatchers(org.springframework.http.HttpMethod.POST, "/api/v1/login", "/api/v1/logout").permitAll()
                        .requestMatchers("/api/v1/**").authenticated().anyRequest().denyAll())
                .exceptionHandling(errors -> errors
                        .authenticationEntryPoint((request, response, error) -> error(response, 401, "Autenticação necessária."))
                        .accessDeniedHandler((request, response, error) -> error(response, 403,
                                error instanceof org.springframework.security.web.csrf.CsrfException
                                        ? "Token CSRF inválido; consulte a sessão." : "Acesso negado.")))
                .formLogin(login -> login.loginPage("/api/v1/login").loginProcessingUrl("/api/v1/login")
                        .usernameParameter("identificador").passwordParameter("senha")
                        .successHandler((request, response, authentication) -> response.setStatus(204))
                        .failureHandler((request, response, error) -> error(response, 401, "Credenciais inválidas.")))
                .logout(logout -> logout.logoutUrl("/api/v1/logout").invalidateHttpSession(true).clearAuthentication(true)
                        .logoutSuccessHandler((request, response, authentication) -> {
                            String path = request.getContextPath().isEmpty() ? "/" : request.getContextPath();
                            response.addHeader("Set-Cookie", "JSESSIONID=; Path=" + path + "; Max-Age=0; HttpOnly; Secure; SameSite=Lax");
                            response.setStatus(204);
                        }))
                .addFilterBefore(new HttpsOnly(), CsrfFilter.class)
                .addFilterBefore(new LoginAttemptsFilter(attempts), UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    static void error(HttpServletResponse response, int status, String message) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.setHeader("Cache-Control", "no-store");
        response.getWriter().write("{\"erro\":\"" + message + "\"}");
    }

    private static final class HttpsOnly extends OncePerRequestFilter {
        @Override
        protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
                throws ServletException, IOException {
            if (request.getServletPath().startsWith("/api/v1/") && !request.isSecure()) {
                error(response, 403, "HTTPS obrigatório.");
                return;
            }
            chain.doFilter(request, response);
        }
    }
}
