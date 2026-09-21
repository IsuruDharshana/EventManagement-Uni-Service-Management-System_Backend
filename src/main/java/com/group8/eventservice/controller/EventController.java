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
import com.group8.eventservice.service.EventService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/events")
@RequiredArgsConstructor
public class EventController {

    private final EventService eventService;

    @PostMapping
    @PreAuthorize("hasAnyRole('ORGANIZER', 'ADMIN_STAFF')")
    public ResponseEntity<EventResponse> createEvent(@Valid @RequestBody CreateEventRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(eventService.createEvent(request));
    }

    @GetMapping
    public List<EventResponse> listEvents() {
        return eventService.listVisibleEvents();
    }

    @GetMapping("/{id}")
    public EventResponse getEvent(@PathVariable UUID id) {
        return eventService.getEventDetail(id);
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasAnyRole('ORGANIZER', 'ADMIN_STAFF')")
    public EventResponse updateEvent(@PathVariable UUID id, @Valid @RequestBody UpdateEventRequest request) {
        return eventService.updateEvent(id, request);
    }

    @PatchMapping("/{id}/publish")
    @PreAuthorize("hasAnyRole('ORGANIZER', 'ADMIN_STAFF')")
    public EventResponse publishEvent(@PathVariable UUID id) {
        return eventService.publishEvent(id);
    }

    @PatchMapping("/{id}/cancel")
    @PreAuthorize("hasAnyRole('ORGANIZER', 'ADMIN_STAFF')")
    public EventResponse cancelEvent(@PathVariable UUID id) {
        return eventService.cancelEvent(id);
    }
}
