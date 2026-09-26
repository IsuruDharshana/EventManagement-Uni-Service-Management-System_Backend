package com.group8.eventservice.security;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.stereotype.Component;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;

/**
 * Dev-only stand-in for Group 5's login: signs RS256 tokens shaped like Group 5's (same
 * {@code sub}, {@code roles}, {@code iss}, {@code aud}) with a key generated at startup, so the
 * API can be tested without the Identity Service. Only exists under the "dev" profile, and the
 * key changes on every restart, so its tokens never outlive the process.
 */
@Component
@Profile("dev")
public class DevTokenIssuer {

    public static final String KEY_ID = "event-service-dev";

    private final RSAPublicKey publicKey;
    private final NimbusJwtEncoder encoder;
    private final String issuer;
    private final String audience;
    private final Duration lifetime;

    public DevTokenIssuer(@Value("${jwt.issuer}") String issuer,
                          @Value("${jwt.audience}") String audience,
                          @Value("${jwt.dev-token-lifetime:PT1H}") Duration lifetime) {
        KeyPair keyPair = generateRsaKeyPair();
        this.publicKey = (RSAPublicKey) keyPair.getPublic();
        RSAKey jwk = new RSAKey.Builder(publicKey)
                .privateKey((RSAPrivateKey) keyPair.getPrivate())
                .keyID(KEY_ID)
                .build();
        this.encoder = new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(jwk)));
        this.issuer = issuer;
        this.audience = audience;
        this.lifetime = lifetime;
    }

    public String issue(String userId, List<String> roles) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject(userId)
                .claim(JwtAuthFilter.ROLES_CLAIM, roles)
                .issuer(issuer)
                .audience(List.of(audience))
                .issuedAt(now)
                .expiresAt(now.plus(lifetime))
                .build();
        JwsHeader header = JwsHeader.with(SignatureAlgorithm.RS256).keyId(KEY_ID).build();
        return encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }

    public RSAPublicKey publicKey() {
        return publicKey;
    }

    private static KeyPair generateRsaKeyPair() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            return generator.generateKeyPair();
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("RSA is not available", ex);
        }
    }
}
