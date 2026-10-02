package dev.arepa.requests.config;

import static org.assertj.core.api.Assertions.*;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

class OidcSecurityTest {
    private Jwt token(String subject, String issuer, String audience, Instant expiry, Map<String,Object> extra) {
        return Jwt.withTokenValue("test").header("alg","RS256").subject(subject).issuer(issuer)
                .audience(List.of(audience)).issuedAt(expiry.minusSeconds(600))
                .expiresAt(expiry).claims(claims -> claims.putAll(extra)).build();
    }
    @Test void validatesIssuerAudienceExpiryAndSubject() {
        var validator = SecurityConfiguration.validators("https://identity.example/realms/requests","service-request-api");
        var issuer = "https://identity.example/realms/requests";
        var future = Instant.now().plusSeconds(300);
        assertThat(validator.validate(token("subject-123",issuer,"service-request-api",future,Map.of())).hasErrors()).isFalse();
        assertThat(validator.validate(token("subject-123","https://other.example","service-request-api",future,Map.of())).hasErrors()).isTrue();
        assertThat(validator.validate(token("subject-123",issuer,"other-api",future,Map.of())).hasErrors()).isTrue();
        assertThat(validator.validate(token("subject-123",issuer,"service-request-api",Instant.now().minusSeconds(120),Map.of())).hasErrors()).isTrue();
        assertThat(validator.validate(token(" ",issuer,"service-request-api",future,Map.of())).hasErrors()).isTrue();
        assertThat(validator.validate(token("s".repeat(201),issuer,"service-request-api",future,Map.of())).hasErrors()).isTrue();
    }
    @Test void rolesAreDerivedFromVerifiedClaimsAndIdentityRemainsSubject() {
        var jwt = token("user-uuid","https://identity.example","service-request-api",Instant.now().plusSeconds(300),
                Map.of("preferred_username","not-operator", "realm_access",Map.of("roles",List.of("operator","unrelated"))));
        var authentication = SecurityConfiguration.roleConverter().convert(jwt);
        assertThat(authentication.getName()).isEqualTo("user-uuid");
        assertThat(authentication.getAuthorities()).extracting("authority").containsExactly("ROLE_OPERATOR");
        var unprivileged = token("operator","https://identity.example","service-request-api",Instant.now().plusSeconds(300),Map.of());
        assertThat(SecurityConfiguration.roleConverter().convert(unprivileged).getAuthorities()).isEmpty();
    }
}
