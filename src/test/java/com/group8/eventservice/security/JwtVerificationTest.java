package com.group8.eventservice.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import com.group8.eventservice.config.JwtConfig;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.sun.net.httpserver.HttpServer;

/** Group 5 token verification: RS256 via a fake Group 5 JWKS endpoint, plus issuer/audience/expiry checks. */
class JwtVerificationTest {

    private static final String ISSUER = "university-identity-service";
    private static final String AUDIENCE = "university-services-platform";

    private HttpServer group5;
    private RSAKey group5Key;
    private JwtDecoder decoder;

    @BeforeEach
    void startGroup5() throws IOException {
        group5Key = rsaKey("group5-key-1");
        byte[] jwks = new JWKSet(group5Key.toPublicJWK()).toString().getBytes(StandardCharsets.UTF_8);

        group5 = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        group5.createContext("/.well-known/jwks.json", exchange -> {
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, jwks.length);
            exchange.getResponseBody().write(jwks);
            exchange.close();
        });
        group5.start();

        decoder = new JwtConfig().jwtDecoder(jwksUri(), ISSUER, AUDIENCE, Optional.empty());
    }

    @AfterEach
    void stopGroup5() {
        group5.stop(0);
    }

    private String jwksUri() {
        return "http://localhost:" + group5.getAddress().getPort() + "/.well-known/jwks.json";
    }

    private static RSAKey rsaKey(String keyId) {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            var pair = generator.generateKeyPair();
            return new RSAKey.Builder((RSAPublicKey) pair.getPublic())
                    .privateKey((RSAPrivateKey) pair.getPrivate()).keyID(keyId).build();
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private static String sign(RSAKey key, String issuer, String audience, Instant expiresAt) {
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject("usr-student-001")
                .claim("roles", List.of("STUDENT"))
                .claim("account_type", "STUDENT")
                .issuer(issuer)
                .audience(List.of(audience))
                .issuedAt(expiresAt.minus(Duration.ofHours(1)))
                .expiresAt(expiresAt)
                .build();
        JwsHeader header = JwsHeader.with(SignatureAlgorithm.RS256).keyId(key.getKeyID()).build();
        return new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(key)))
                .encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }

    private String group5Token() {
        return sign(group5Key, ISSUER, AUDIENCE, Instant.now().plus(Duration.ofHours(1)));
    }

    @Test
    void acceptsGroup5TokenAndReadsSubjectAndRoles() {
        Jwt jwt = decoder.decode(group5Token());

        assertThat(jwt.getSubject()).isEqualTo("usr-student-001");
        assertThat(jwt.getClaimAsStringList("roles")).containsExactly("STUDENT");
    }

    @Test
    void rejectsWrongIssuer() {
        String token = sign(group5Key, "someone-else", AUDIENCE, Instant.now().plus(Duration.ofHours(1)));
        assertThatThrownBy(() -> decoder.decode(token)).isInstanceOf(JwtException.class);
    }

    @Test
    void rejectsWrongAudience() {
        String token = sign(group5Key, ISSUER, "another-platform", Instant.now().plus(Duration.ofHours(1)));
        assertThatThrownBy(() -> decoder.decode(token)).isInstanceOf(JwtException.class);
    }

    @Test
    void rejectsExpiredToken() {
        String token = sign(group5Key, ISSUER, AUDIENCE, Instant.now().minus(Duration.ofMinutes(5)));
        assertThatThrownBy(() -> decoder.decode(token)).isInstanceOf(JwtException.class);
    }

    @Test
    void rejectsTokenSignedWithAKeyGroup5DoesNotPublish() {
        String token = sign(rsaKey("group5-key-1"), ISSUER, AUDIENCE, Instant.now().plus(Duration.ofHours(1)));
        assertThatThrownBy(() -> decoder.decode(token)).isInstanceOf(JwtException.class);
    }

    @Test
    void rejectsTamperedToken() {
        String token = group5Token();
        String tampered = token.substring(0, token.length() - 4) + "abcd";
        assertThatThrownBy(() -> decoder.decode(tampered)).isInstanceOf(JwtException.class);
    }

    @Test
    void rejectsEverythingWhenGroup5KeysCannotBeFetched() {
        JwtDecoder offline = new JwtConfig().jwtDecoder("http://localhost:1/.well-known/jwks.json",
                ISSUER, AUDIENCE, Optional.empty());
        assertThatThrownBy(() -> offline.decode(group5Token())).isInstanceOf(JwtException.class);
    }

    @Test
    void devTokensAreOnlyAcceptedWhenTheDevIssuerIsActive() {
        DevTokenIssuer devIssuer = new DevTokenIssuer(ISSUER, AUDIENCE, Duration.ofHours(1));
        String devToken = devIssuer.issue("usr-organizer-001", List.of("EVENT_ORGANIZER"));

        assertThatThrownBy(() -> decoder.decode(devToken)).isInstanceOf(JwtException.class);

        JwtDecoder devDecoder = new JwtConfig().jwtDecoder(jwksUri(), ISSUER, AUDIENCE, Optional.of(devIssuer));
        assertThat(devDecoder.decode(devToken).getClaimAsStringList("roles")).containsExactly("EVENT_ORGANIZER");
        assertThat(devDecoder.decode(group5Token()).getSubject()).isEqualTo("usr-student-001");
    }
}
