package com.group8.eventservice.security;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Throwaway controller used only by {@link SecurityFilterChainTest} to exercise role gating. */
@RestController
public class TestProtectedController {

    @GetMapping("/api/test/organizer-only")
    @PreAuthorize("hasRole('ORGANIZER')")
    public String organizerOnly() {
        return "ok";
    }

    @GetMapping("/api/test/any-authenticated")
    public String anyAuthenticated() {
        return "ok";
    }
}
