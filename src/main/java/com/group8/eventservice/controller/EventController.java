package com.group8.eventservice.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.group8.eventservice.dto.request.CreateEventRequest;
import com.group8.eventservice.dto.request.UpdateEventRequest;
import com.group8.eventservice.dto.response.EventResponse;
import com.group8.eventservice.exception.ApiErrorResponse;
import com.group8.eventservice.service.EventService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/events")
@RequiredArgsConstructor
@Tag(name = "Events", description = "Create, edit, publish, cancel and view events")
public class EventController {

    private static final String ERR = "application/json";

    private final EventService eventService;

    @PostMapping
    @PreAuthorize("hasAnyRole('ORGANIZER', 'ADMIN_STAFF')")
    @Operation(summary = "Create a draft event", description = "Roles: ORGANIZER, ADMIN_STAFF. The caller becomes the event's organizer.")
    @ApiResponse(responseCode = "201", description = "Event created in DRAFT status")
    @ApiResponse(responseCode = "400", description = "VALIDATION_ERROR: invalid or missing fields, scheduleEnd not after scheduleStart, or registrationCloseAt after scheduleStart",
            content = @Content(mediaType = ERR, schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(responseCode = "401", description = "UNAUTHORIZED: missing, invalid or expired token",
            content = @Content(mediaType = ERR, schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(responseCode = "403", description = "FORBIDDEN: caller is not ORGANIZER or ADMIN_STAFF",
            content = @Content(mediaType = ERR, schema = @Schema(implementation = ApiErrorResponse.class)))
    public ResponseEntity<EventResponse> createEvent(@Valid @RequestBody CreateEventRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(eventService.createEvent(request));
    }

    @GetMapping
    @Operation(summary = "List events visible to the caller",
            description = "Everyone sees PUBLISHED events. Organizers also see their own non-published events. ADMIN_STAFF see all.")
    @ApiResponse(responseCode = "200", description = "Visible events")
    @ApiResponse(responseCode = "401", description = "UNAUTHORIZED",
            content = @Content(mediaType = ERR, schema = @Schema(implementation = ApiErrorResponse.class)))
    public List<EventResponse> listEvents() {
        return eventService.listVisibleEvents();
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get event detail")
    @ApiResponse(responseCode = "200", description = "Event detail")
    @ApiResponse(responseCode = "401", description = "UNAUTHORIZED",
            content = @Content(mediaType = ERR, schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(responseCode = "403", description = "NOT_VISIBLE: event is not published and caller is neither its organizer nor ADMIN_STAFF",
            content = @Content(mediaType = ERR, schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(responseCode = "404", description = "NOT_FOUND",
            content = @Content(mediaType = ERR, schema = @Schema(implementation = ApiErrorResponse.class)))
    public EventResponse getEvent(@PathVariable UUID id) {
        return eventService.getEventDetail(id);
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasAnyRole('ORGANIZER', 'ADMIN_STAFF')")
    @Operation(summary = "Partially update an event", description = "Only provided fields change. Roles: the owning ORGANIZER or ADMIN_STAFF.")
    @ApiResponse(responseCode = "200", description = "Updated event")
    @ApiResponse(responseCode = "400", description = "VALIDATION_ERROR or INVALID_SCHEDULE: resulting schedule is inconsistent",
            content = @Content(mediaType = ERR, schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(responseCode = "401", description = "UNAUTHORIZED",
            content = @Content(mediaType = ERR, schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(responseCode = "403", description = "FORBIDDEN: caller does not own the event",
            content = @Content(mediaType = ERR, schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(responseCode = "404", description = "NOT_FOUND",
            content = @Content(mediaType = ERR, schema = @Schema(implementation = ApiErrorResponse.class)))
    public EventResponse updateEvent(@PathVariable UUID id, @Valid @RequestBody UpdateEventRequest request) {
        return eventService.updateEvent(id, request);
    }

    @PatchMapping("/{id}/publish")
    @PreAuthorize("hasAnyRole('ORGANIZER', 'ADMIN_STAFF')")
    @Operation(summary = "Publish a draft event",
            description = "Roles: the owning ORGANIZER or ADMIN_STAFF. Physical-venue events are checked with Group 6 first; online events skip the check.")
    @ApiResponse(responseCode = "400", description = "INVALID_STATE (not a draft) or VENUE_NOT_FOUND: Group 6 does not know the venue code",
            content = @Content(mediaType = ERR, schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(responseCode = "409", description = "VENUE_NOT_AVAILABLE: Group 6 says the venue cannot be used",
            content = @Content(mediaType = ERR, schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(responseCode = "503", description = "GROUP6_UNAVAILABLE: venue service unreachable, event stays DRAFT",
            content = @Content(mediaType = ERR, schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(responseCode = "200", description = "Event is now PUBLISHED")
    @ApiResponse(responseCode = "400", description = "INVALID_STATE: only DRAFT events can be published",
            content = @Content(mediaType = ERR, schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(responseCode = "401", description = "UNAUTHORIZED",
            content = @Content(mediaType = ERR, schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(responseCode = "403", description = "FORBIDDEN: caller does not own the event",
            content = @Content(mediaType = ERR, schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(responseCode = "404", description = "NOT_FOUND",
            content = @Content(mediaType = ERR, schema = @Schema(implementation = ApiErrorResponse.class)))
    public EventResponse publishEvent(@PathVariable UUID id) {
        return eventService.publishEvent(id);
    }

    @PatchMapping("/{id}/cancel")
    @PreAuthorize("hasAnyRole('ORGANIZER', 'ADMIN_STAFF')")
    @Operation(summary = "Cancel an event", description = "Roles: the owning ORGANIZER or ADMIN_STAFF.")
    @ApiResponse(responseCode = "200", description = "Event is now CANCELLED")
    @ApiResponse(responseCode = "400", description = "INVALID_STATE: event is already cancelled or completed",
            content = @Content(mediaType = ERR, schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(responseCode = "401", description = "UNAUTHORIZED",
            content = @Content(mediaType = ERR, schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(responseCode = "403", description = "FORBIDDEN: caller does not own the event",
            content = @Content(mediaType = ERR, schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(responseCode = "404", description = "NOT_FOUND",
            content = @Content(mediaType = ERR, schema = @Schema(implementation = ApiErrorResponse.class)))
    public EventResponse cancelEvent(@PathVariable UUID id) {
        return eventService.cancelEvent(id);
    }
}
