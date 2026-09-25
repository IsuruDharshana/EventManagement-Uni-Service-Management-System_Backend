package com.group8.eventservice.controller;

import java.util.UUID;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import com.group8.eventservice.dto.response.EventRegistrationSummary;
import com.group8.eventservice.dto.response.EventsOverview;
import com.group8.eventservice.exception.ApiErrorResponse;
import com.group8.eventservice.service.SummaryService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@Tag(name = "Summaries", description = "Participation numbers for organizers and admin staff")
public class SummaryController {

    private static final String ERR = "application/json";

    private final SummaryService summaryService;

    @GetMapping("/api/events/{eventId}/registrations")
    @PreAuthorize("hasAnyRole('ORGANIZER', 'ADMIN_STAFF')")
    @Operation(summary = "Registration and capacity summary for one event",
            description = "Roles: the owning ORGANIZER or ADMIN_STAFF.")
    @ApiResponse(responseCode = "200", description = "Confirmed and cancelled counts and remaining seats")
    @ApiResponse(responseCode = "401", description = "UNAUTHORIZED",
            content = @Content(mediaType = ERR, schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(responseCode = "403", description = "FORBIDDEN: caller does not own the event",
            content = @Content(mediaType = ERR, schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(responseCode = "404", description = "NOT_FOUND",
            content = @Content(mediaType = ERR, schema = @Schema(implementation = ApiErrorResponse.class)))
    public EventRegistrationSummary eventSummary(@PathVariable UUID eventId) {
        return summaryService.summaryForEvent(eventId);
    }

    @GetMapping("/api/events/summary")
    @PreAuthorize("hasRole('ADMIN_STAFF')")
    @Operation(summary = "Overall event and registration totals", description = "Role: ADMIN_STAFF.")
    @ApiResponse(responseCode = "200", description = "Totals by event status and registration status")
    @ApiResponse(responseCode = "401", description = "UNAUTHORIZED",
            content = @Content(mediaType = ERR, schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(responseCode = "403", description = "FORBIDDEN: caller is not ADMIN_STAFF",
            content = @Content(mediaType = ERR, schema = @Schema(implementation = ApiErrorResponse.class)))
    public EventsOverview overview() {
        return summaryService.overview();
    }
}
