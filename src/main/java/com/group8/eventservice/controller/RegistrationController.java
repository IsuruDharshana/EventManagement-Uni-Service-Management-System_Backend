package com.group8.eventservice.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import com.group8.eventservice.dto.response.RegistrationResponse;
import com.group8.eventservice.service.RegistrationService;

import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
public class RegistrationController {

    private final RegistrationService registrationService;

    @PostMapping("/api/events/{eventId}/registrations")
    public ResponseEntity<RegistrationResponse> register(@PathVariable UUID eventId) {
        return ResponseEntity.status(HttpStatus.CREATED).body(registrationService.register(eventId));
    }

    @PatchMapping("/api/registrations/{id}/cancel")
    public RegistrationResponse cancelRegistration(@PathVariable UUID id) {
        return registrationService.cancelRegistration(id);
    }

    @GetMapping("/api/registrations/mine")
    public List<RegistrationResponse> myRegistrations() {
        return registrationService.myRegistrations();
    }
}
