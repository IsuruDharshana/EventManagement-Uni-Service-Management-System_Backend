package com.group8.eventservice.controller;

import java.util.Map;

import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.group8.eventservice.security.JwtUtil;

import lombok.RequiredArgsConstructor;

/**
 * TEMPORARY: mints a JWT with an arbitrary role for local/dev testing, standing in for
 * Group 5's real auth until it issues tokens. Only registered under the "dev" profile
 * ({@code SPRING_PROFILES_ACTIVE=dev}) — absent from docker/prod deployments. Remove once
 * Group 5's real token issuance is wired up.
 */
@RestController
@RequestMapping("/api/dev")
@Profile("dev")
@RequiredArgsConstructor
public class TestTokenController {

    private final JwtUtil jwtUtil;

    @PostMapping("/token")
    public Map<String, String> mintToken(@RequestParam String userId, @RequestParam String role) {
        return Map.of("token", jwtUtil.generateToken(userId, role));
    }
}
