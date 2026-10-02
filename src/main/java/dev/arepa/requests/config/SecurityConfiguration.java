package dev.arepa.requests.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;
import org.springframework.web.filter.OncePerRequestFilter;

@Configuration
public class SecurityConfiguration {
    @Bean
    SecurityFilterChain security(HttpSecurity http,
            @Value("${platform.requester-token}") String requester,
            @Value("${platform.operator-token}") String operator) throws Exception {
        if (requester.length() < 32 || operator.length() < 32 || requester.equals(operator)) throw new IllegalStateException("Configure distinct requester and operator tokens of at least 32 characters");
        return http.csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .headers(headers -> headers.contentSecurityPolicy(csp -> csp.policyDirectives("default-src 'self'; script-src 'self'; style-src 'self'; img-src 'self'; frame-ancestors 'none'; base-uri 'self'; form-action 'self'")))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/", "/index.html", "/app.js", "/styles.css", "/actuator/health", "/actuator/health/**", "/openapi.yaml").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/requests/*/transitions").hasRole("OPERATOR")
                        .requestMatchers("/api/**").authenticated()
                        .requestMatchers("/actuator/**").hasRole("OPERATOR")
                        .anyRequest().denyAll())
                .exceptionHandling(errors -> errors
                        .authenticationEntryPoint((req, res, ex) -> problem(res, 401, "Authentication required"))
                        .accessDeniedHandler((req, res, ex) -> problem(res, 403, "Operator role required")))
                .addFilterBefore(new TokenFilter(requester, operator), AnonymousAuthenticationFilter.class)
                .build();
    }

    private static void problem(HttpServletResponse response, int status, String title) throws IOException {
        response.setStatus(status);
        if (status == 401) response.setHeader("WWW-Authenticate", "Bearer");
        response.setContentType("application/problem+json");
        response.getWriter().write("{\"type\":\"about:blank\",\"status\":" + status + ",\"title\":\"" + title + "\"}");
    }

    private static final class TokenFilter extends OncePerRequestFilter {
        private final byte[] requester;
        private final byte[] operator;
        TokenFilter(String requester, String operator) {
            this.requester = requester.getBytes(StandardCharsets.UTF_8);
            this.operator = operator.getBytes(StandardCharsets.UTF_8);
        }
        @Override
        protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws ServletException, IOException {
            var header = request.getHeader("Authorization");
            if (header != null && header.startsWith("Bearer ")) {
                var token = header.substring(7).getBytes(StandardCharsets.UTF_8);
                String identity = null;
                if (MessageDigest.isEqual(operator, token)) identity = "operator";
                else if (MessageDigest.isEqual(requester, token)) identity = "requester";
                if (identity != null) {
                    var authentication = new UsernamePasswordAuthenticationToken(identity, null,
                            List.of(new SimpleGrantedAuthority("ROLE_" + identity.toUpperCase(java.util.Locale.ROOT))));
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                }
            }
            chain.doFilter(request, response);
        }
    }
}
