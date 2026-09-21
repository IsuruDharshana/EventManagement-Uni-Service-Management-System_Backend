package com.group8.eventservice.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;

class JwtUtilTest {

    private static final String SECRET = "unit-test-secret-key-must-be-at-least-32-bytes-long";

    @Test
    void validTokenRoundTripsSubjectAndRole() {
        JwtUtil jwtUtil = new JwtUtil(SECRET, 60_000);

        String token = jwtUtil.generateToken("user-123", "ORGANIZER");
        var claims = jwtUtil.parseToken(token).getPayload();

        assertThat(claims.getSubject()).isEqualTo("user-123");
        assertThat(claims.get(JwtUtil.ROLE_CLAIM, String.class)).isEqualTo("ORGANIZER");
    }

    @Test
    void expiredTokenIsRejected() throws InterruptedException {
        JwtUtil jwtUtil = new JwtUtil(SECRET, 1);

        String token = jwtUtil.generateToken("user-123", "ORGANIZER");
        Thread.sleep(10);

        assertThrows(ExpiredJwtException.class, () -> jwtUtil.parseToken(token));
    }

    @Test
    void tamperedTokenIsRejected() {
        JwtUtil jwtUtil = new JwtUtil(SECRET, 60_000);
        String token = jwtUtil.generateToken("user-123", "ORGANIZER");

        String tampered = token.substring(0, token.length() - 4) + "abcd";

        assertThrows(JwtException.class, () -> jwtUtil.parseToken(tampered));
    }

    @Test
    void tokenSignedWithDifferentSecretIsRejected() {
        JwtUtil signer = new JwtUtil(SECRET, 60_000);
        JwtUtil verifier = new JwtUtil("a-completely-different-secret-key-32-bytes-plus", 60_000);

        String token = signer.generateToken("user-123", "ORGANIZER");

        assertThrows(JwtException.class, () -> verifier.parseToken(token));
    }
}
