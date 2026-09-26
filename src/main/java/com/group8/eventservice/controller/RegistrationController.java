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
import com.group8.eventservice.exception.ApiErrorResponse;
import com.group8.eventservice.service.RegistrationService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@Tag(name = "Registrations", description = "Register for events, cancel, and view your registrations")
public class RegistrationController {

    private static final String ERR = "application/json";

    private final RegistrationService registrationService;

    @PostMapping("/api/events/{eventId}/registrations")
    @Operation(summary = "Register the caller for an event",
            description = "Any signed-in user. Checks, in order: event is PUBLISHED, registration window is open, "
                    + "Group 5 eligibility (skipped for {\"all\": true} events), capacity.")
    @ApiResponse(responseCode = "201", description = "Registration CONFIRMED")
    @ApiResponse(responseCode = "400", description = "EVENT_NOT_PUBLISHED or REGISTRATION_CLOSED",
            content = @Content(mediaType = ERR, schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(responseCode = "401", description = "UNAUTHORIZED",
            content = @Content(mediaType = ERR, schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(responseCode = "403", description = "NOT_ELIGIBLE: Group 5 says the caller does not meet the event's eligibility rule (message explains why), "
            + "or INVALID_USER: Group 5 does not know the caller or the account is inactive",
            content = @Content(mediaType = ERR, schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(responseCode = "404", description = "NOT_FOUND: event does not exist",
            content = @Content(mediaType = ERR, schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(responseCode = "409", description = "CAPACITY_REACHED or ALREADY_REGISTERED",
            content = @Content(mediaType = ERR, schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(responseCode = "503", description = "GROUP5_UNAVAILABLE: eligibility service unreachable, nothing was saved",
            content = @Content(mediaType = ERR, schema = @Schema(implementation = ApiErrorResponse.class)))
    public ResponseEntity<RegistrationResponse> register(@PathVariable UUID eventId) {
        return ResponseEntity.status(HttpStatus.CREATED).body(registrationService.register(eventId));
    }

    @PatchMapping("/api/registrations/{id}/cancel")
    @Operation(summary = "Cancel the caller's own registration", description = "Not allowed once the event has started.")
    @ApiResponse(responseCode = "200", description = "Registration is now CANCELLED")
    @ApiResponse(responseCode = "400", description = "INVALID_STATE (already cancelled) or CANCELLATION_CLOSED (event started)",
            content = @Content(mediaType = ERR, schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(responseCode = "401", description = "UNAUTHORIZED",
            content = @Content(mediaType = ERR, schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(responseCode = "403", description = "FORBIDDEN: registration belongs to another user",
            content = @Content(mediaType = ERR, schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(responseCode = "404", description = "NOT_FOUND",
            content = @Content(mediaType = ERR, schema = @Schema(implementation = ApiErrorResponse.class)))
    public RegistrationResponse cancelRegistration(@PathVariable UUID id) {
        return registrationService.cancelRegistration(id);
    }

    @GetMapping("/api/registrations/mine")
    @Operation(summary = "List the caller's registrations, including cancelled ones")
    @ApiResponse(responseCode = "200", description = "Registration history")
    @ApiResponse(responseCode = "401", description = "UNAUTHORIZED",
            content = @Content(mediaType = ERR, schema = @Schema(implementation = ApiErrorResponse.class)))
    public List<RegistrationResponse> myRegistrations() {
        return registrationService.myRegistrations();
    }
}
