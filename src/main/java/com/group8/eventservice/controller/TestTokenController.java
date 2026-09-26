package com.group8.eventservice.controller;

import java.util.List;
import java.util.Map;

import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.group8.eventservice.security.DevTokenIssuer;

import lombok.RequiredArgsConstructor;

/**
 * DEV ONLY: mints a Group 5-shaped token for local testing without the Identity Service, e.g.
 * {@code POST /api/dev/token?userId=usr-organizer-001&roles=EVENT_ORGANIZER}. Several roles can
 * be comma-separated. Only registered under the "dev" profile ({@code SPRING_PROFILES_ACTIVE=dev})
 * — never enable that profile in a shared or production deployment.
 */
@RestController
@RequestMapping("/api/dev")
@Profile("dev")
@RequiredArgsConstructor
public class TestTokenController {

    private final DevTokenIssuer devTokenIssuer;

    @PostMapping("/token")
    public Map<String, String> mintToken(@RequestParam String userId, @RequestParam List<String> roles) {
        return Map.of("token", devTokenIssuer.issue(userId, roles));
    }
}
