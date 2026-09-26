package com.group8.eventservice.config;

import java.text.ParseException;
import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.web.client.RestTemplate;

import com.group8.eventservice.security.DevTokenIssuer;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jwt.JWTParser;

/**
 * Verifies Group 5 access tokens: RS256 signature checked against Group 5's public keys
 * (JWKS, fetched lazily and cached), plus expiry, issuer and audience. Under the "dev"
 * profile, tokens from {@link DevTokenIssuer} (recognised by their key id) are verified with
 * its local key instead, using the same claim checks.
 */
@Configuration
public class JwtConfig {

    @Bean
    public JwtDecoder jwtDecoder(@Value("${jwt.jwks-uri}") String jwksUri,
                                 @Value("${jwt.issuer}") String issuer,
                                 @Value("${jwt.audience}") String audience,
                                 Optional<DevTokenIssuer> devTokenIssuer) {
        OAuth2TokenValidator<Jwt> validator = claimValidator(issuer, audience);

        NimbusJwtDecoder group5 = NimbusJwtDecoder.withJwkSetUri(jwksUri).restOperations(jwksClient()).build();
        group5.setJwtValidator(validator);

        if (devTokenIssuer.isEmpty()) {
            return group5;
        }

        NimbusJwtDecoder dev = NimbusJwtDecoder.withPublicKey(devTokenIssuer.get().publicKey()).build();
        dev.setJwtValidator(validator);
        return token -> DevTokenIssuer.KEY_ID.equals(keyIdOf(token)) ? dev.decode(token) : group5.decode(token);
    }

    static OAuth2TokenValidator<Jwt> claimValidator(String issuer, String audience) {
        JwtClaimValidator<List<String>> audienceValidator = new JwtClaimValidator<>(JwtClaimNames.AUD,
                aud -> aud != null && aud.contains(audience));
        return new DelegatingOAuth2TokenValidator<>(JwtValidators.createDefaultWithIssuer(issuer), audienceValidator);
    }

    private static RestTemplate jwksClient() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(3_000);
        factory.setReadTimeout(5_000);
        return new RestTemplate(factory);
    }

    private static String keyIdOf(String token) {
        try {
            return JWTParser.parse(token).getHeader() instanceof JWSHeader header ? header.getKeyID() : null;
        } catch (ParseException ex) {
            throw new BadJwtException("Malformed token", ex);
        }
    }
}
