package com.group8.eventservice.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

public final class SecurityUtils {

    private SecurityUtils() {
    }

    /** Group 5 user id from the token's {@code sub} claim, e.g. {@code usr-student-001}. */
    public static String currentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return (String) auth.getPrincipal();
    }

    /** The caller's raw JWT, forwarded to Group 5 when validating them. Null if not available. */
    public static String currentBearerToken() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth.getCredentials() instanceof String token ? token : null;
    }

    public static boolean currentUserHasRole(String role) {
        return currentUserHasAnyRole(role);
    }

    public static boolean currentUserHasAnyRole(String... roles) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        for (String role : roles) {
            String authority = "ROLE_" + role;
            if (auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals(authority))) {
                return true;
            }
        }
        return false;
    }
}
