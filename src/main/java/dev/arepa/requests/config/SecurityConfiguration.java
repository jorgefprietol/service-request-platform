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
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;

@Configuration
public class SecurityConfiguration {
    @Bean
    SecurityFilterChain security(HttpSecurity http,
            @Value("${platform.auth-mode}") String mode,
            @Value("${platform.oidc-issuer}") String issuer,
            @Value("${platform.oidc-jwks}") String jwks,
            @Value("${platform.oidc-audience}") String audience,
            @Value("${platform.requester-token}") String requester,
            @Value("${platform.operator-token}") String operator) throws Exception {
        if (mode.equals("oidc")) {
            var decoder = NimbusJwtDecoder.withJwkSetUri(jwks).build();
            decoder.setJwtValidator(validators(issuer, audience));
            http.oauth2ResourceServer(resource -> resource.jwt(jwt -> jwt.decoder(decoder).jwtAuthenticationConverter(roleConverter()))
                    .authenticationEntryPoint((req,res,error) -> problem(res,401,"Invalid or expired access token")));
        } else if (mode.equals("development-tokens")) {
            if (requester.length() < 32 || operator.length() < 32 || requester.equals(operator)) throw new IllegalStateException("Configure distinct development tokens of at least 32 characters");
            http.addFilterBefore(new TokenFilter(requester,operator), AnonymousAuthenticationFilter.class);
        } else throw new IllegalStateException("Use oidc or development-tokens authentication mode");
        return http.csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .headers(headers -> headers.contentSecurityPolicy(csp -> csp.policyDirectives("default-src 'self'; script-src 'self'; style-src 'self'; img-src 'self'; connect-src 'self' " + java.net.URI.create(issuer).resolve("/") + "; frame-ancestors 'none'; base-uri 'self'; form-action 'self'")))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/", "/index.html", "/app.js", "/styles.css", "/actuator/health", "/actuator/health/**", "/openapi.yaml", "/api/auth/config").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/requests/*/transitions").hasRole("OPERATOR")
                        .requestMatchers(HttpMethod.POST, "/api/requests/*/assignment").hasRole("OPERATOR")
                        .requestMatchers("/api/operators", "/api/alerts").hasRole("OPERATOR")
                        .requestMatchers("/api/**").hasAnyRole("REQUESTER","OPERATOR")
                        .requestMatchers("/actuator/**").hasRole("OPERATOR")
                        .anyRequest().denyAll())
                .exceptionHandling(errors -> errors
                        .authenticationEntryPoint((req, res, ex) -> problem(res, 401, "Authentication required"))
                        .accessDeniedHandler((req, res, ex) -> problem(res, 403, "Operator role required")))
                .build();
    }

    static OAuth2TokenValidator<Jwt> validators(String issuer, String audience) {
        OAuth2TokenValidator<Jwt> claims = jwt -> {
            if (jwt.getAudience() == null || !jwt.getAudience().contains(audience) || jwt.getSubject() == null || jwt.getSubject().isBlank() || jwt.getSubject().length() > 200) {
                return OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "Audience or subject is invalid", null));
            }
            return OAuth2TokenValidatorResult.success();
        };
        return new DelegatingOAuth2TokenValidator<>(JwtValidators.createDefaultWithIssuer(issuer), claims);
    }

    static JwtAuthenticationConverter roleConverter() {
        var converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(jwt -> {
            var access = jwt.getClaimAsMap("realm_access");
            if (access == null || !(access.get("roles") instanceof List<?> roles)) return List.of();
            return roles.stream().filter(role -> role.equals("requester") || role.equals("operator"))
                    .map(role -> (org.springframework.security.core.GrantedAuthority) new SimpleGrantedAuthority("ROLE_" + role.toString().toUpperCase(java.util.Locale.ROOT))).toList();
        });
        return converter;
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
