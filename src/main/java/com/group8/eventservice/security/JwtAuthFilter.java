package com.group8.eventservice.security;

import java.io.IOException;
import java.util.List;

import org.springframework.http.HttpHeaders;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Reads the Authorization: Bearer <token> header and, when the Group 5 token is valid, populates
 * the SecurityContext with the user id ({@code sub}), one authority per entry in the {@code roles}
 * claim, and the raw token (so it can be forwarded to Group 5). Leaves the context empty on any
 * failure — a missing/invalid/expired token then falls through to the entry point as a 401.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthFilter extends OncePerRequestFilter {

    public static final String ROLES_CLAIM = "roles";
    /** Group 5 treats a user with no assigned role as having their account_type (STUDENT or STAFF) as the role. */
    public static final String ACCOUNT_TYPE_CLAIM = "account_type";

    private final JwtDecoder jwtDecoder;

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                     @NonNull HttpServletResponse response,
                                     @NonNull FilterChain filterChain) throws ServletException, IOException {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);

        if (header != null && header.startsWith("Bearer ")) {
            String token = header.substring(7);
            try {
                Jwt jwt = jwtDecoder.decode(token);
                String userId = jwt.getSubject();
                if (userId == null || userId.isBlank()) {
                    throw new IllegalArgumentException("Token has no subject");
                }

                var authorities = rolesOf(jwt).stream()
                        .map(role -> new SimpleGrantedAuthority("ROLE_" + role.trim().toUpperCase()))
                        .toList();
                var authentication = new UsernamePasswordAuthenticationToken(userId, token, authorities);
                SecurityContextHolder.getContext().setAuthentication(authentication);
            } catch (JwtException | IllegalArgumentException ex) {
                log.debug("Rejected bearer token: {}", ex.getMessage());
                SecurityContextHolder.clearContext();
            }
        }

        filterChain.doFilter(request, response);
    }

    private static List<String> rolesOf(Jwt jwt) {
        List<String> roles = jwt.getClaimAsStringList(ROLES_CLAIM);
        if (roles != null && !roles.isEmpty()) {
            return roles;
        }
        String accountType = jwt.getClaimAsString(ACCOUNT_TYPE_CLAIM);
        return accountType == null ? List.of() : List.of(accountType);
    }
}
