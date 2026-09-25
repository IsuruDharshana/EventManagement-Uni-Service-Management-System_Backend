package com.group8.eventservice.service;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.group8.eventservice.dto.request.CreateEventRequest;
import com.group8.eventservice.dto.request.UpdateEventRequest;
import com.group8.eventservice.dto.response.EventResponse;
import com.group8.eventservice.entity.Event;
import com.group8.eventservice.entity.EventStatus;
import com.group8.eventservice.exception.ApiException;
import com.group8.eventservice.repository.EventRepository;
import com.group8.eventservice.security.SecurityUtils;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class EventService {

    private static final String ADMIN_STAFF = "ADMIN_STAFF";
    private static final String ORGANIZER = "ORGANIZER";

    private final EventRepository eventRepository;
    private final Group6Client group6Client;

    @Transactional
    public EventResponse createEvent(CreateEventRequest request) {
        Event event = Event.builder()
                .title(request.getTitle())
                .description(request.getDescription())
                .organizerId(SecurityUtils.currentUserId())
                .venue(request.getVenue())
                .online(request.isOnline())
                .scheduleStart(request.getScheduleStart())
                .scheduleEnd(request.getScheduleEnd())
                .capacity(request.getCapacity())
                .eligibilityRule(request.getEligibilityRule())
                .registrationOpenAt(request.getRegistrationOpenAt())
                .registrationCloseAt(request.getRegistrationCloseAt())
                .status(EventStatus.DRAFT)
                .build();

        return EventResponse.from(eventRepository.saveAndFlush(event));
    }

    public List<EventResponse> listVisibleEvents() {
        List<Event> events = SecurityUtils.currentUserHasRole(ADMIN_STAFF)
                ? eventRepository.findAll()
                : eventRepository.findAll().stream()
                        .filter(this::isVisibleToNonAdmin)
                        .toList();

        return events.stream().map(EventResponse::from).toList();
    }

    public EventResponse getEventDetail(UUID id) {
        Event event = findOrThrow(id);

        if (!SecurityUtils.currentUserHasRole(ADMIN_STAFF) && !isVisibleToNonAdmin(event)) {
            throw new ApiException("NOT_VISIBLE", "You do not have access to this event.", HttpStatus.FORBIDDEN);
        }

        return EventResponse.from(event);
    }

    @Transactional
    public EventResponse updateEvent(UUID id, UpdateEventRequest request) {
        Event event = findOrThrow(id);
        requireOwnerOrAdmin(event);

        if (request.getTitle() != null) {
            event.setTitle(request.getTitle());
        }
        if (request.getDescription() != null) {
            event.setDescription(request.getDescription());
        }
        if (request.getVenue() != null) {
            event.setVenue(request.getVenue());
        }
        if (request.getOnline() != null) {
            event.setOnline(request.getOnline());
        }
        if (request.getScheduleStart() != null) {
            event.setScheduleStart(request.getScheduleStart());
        }
        if (request.getScheduleEnd() != null) {
            event.setScheduleEnd(request.getScheduleEnd());
        }
        if (request.getCapacity() != null) {
            event.setCapacity(request.getCapacity());
        }
        if (request.getEligibilityRule() != null) {
            event.setEligibilityRule(request.getEligibilityRule());
        }
        if (request.getRegistrationOpenAt() != null) {
            event.setRegistrationOpenAt(request.getRegistrationOpenAt());
        }
        if (request.getRegistrationCloseAt() != null) {
            event.setRegistrationCloseAt(request.getRegistrationCloseAt());
        }

        validateScheduleCoherence(event);

        return EventResponse.from(eventRepository.saveAndFlush(event));
    }

    @Transactional
    public EventResponse publishEvent(UUID id) {
        Event event = findOrThrow(id);
        requireOwnerOrAdmin(event);

        if (event.getStatus() != EventStatus.DRAFT) {
            throw new ApiException("INVALID_STATE", "Only draft events can be published.", HttpStatus.BAD_REQUEST);
        }

        requireVenueAvailable(event);

        event.setStatus(EventStatus.PUBLISHED);
        return EventResponse.from(eventRepository.saveAndFlush(event));
    }

    @Transactional
    public EventResponse cancelEvent(UUID id) {
        Event event = findOrThrow(id);
        requireOwnerOrAdmin(event);

        if (event.getStatus() == EventStatus.CANCELLED || event.getStatus() == EventStatus.COMPLETED) {
            throw new ApiException("INVALID_STATE",
                    "Event is already " + event.getStatus().name().toLowerCase() + ".", HttpStatus.BAD_REQUEST);
        }

        event.setStatus(EventStatus.CANCELLED);
        return EventResponse.from(eventRepository.saveAndFlush(event));
    }

    private void requireVenueAvailable(Event event) {
        if (event.isOnline() || event.getVenue() == null || event.getVenue().isBlank()) {
            return;
        }

        VenueResult venue = group6Client.checkAvailability(event.getVenue(), event.getId());
        if (!venue.isServiceAvailable()) {
            throw new ApiException("GROUP6_UNAVAILABLE",
                    "Venue check is temporarily unavailable. Please try publishing again shortly.", HttpStatus.SERVICE_UNAVAILABLE);
        }
        if (!venue.isAvailable()) {
            throw new ApiException("VENUE_NOT_AVAILABLE",
                    "The venue is not available for this event.", HttpStatus.CONFLICT);
        }
    }

    private Event findOrThrow(UUID id) {
        return eventRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Event " + id + " not found"));
    }

    private void requireOwnerOrAdmin(Event event) {
        if (SecurityUtils.currentUserHasRole(ADMIN_STAFF)) {
            return;
        }
        if (SecurityUtils.currentUserHasRole(ORGANIZER) && event.getOrganizerId().equals(SecurityUtils.currentUserId())) {
            return;
        }
        throw new ApiException("FORBIDDEN", "You do not own this event.", HttpStatus.FORBIDDEN);
    }

    private boolean isVisibleToNonAdmin(Event event) {
        return event.getStatus() == EventStatus.PUBLISHED
                || event.getOrganizerId().equals(SecurityUtils.currentUserId());
    }

    private void validateScheduleCoherence(Event event) {
        if (event.getScheduleEnd().isBefore(event.getScheduleStart())
                || event.getScheduleEnd().isEqual(event.getScheduleStart())) {
            throw new ApiException("INVALID_SCHEDULE", "scheduleEnd must be after scheduleStart.", HttpStatus.BAD_REQUEST);
        }
        if (event.getRegistrationCloseAt().isAfter(event.getScheduleStart())) {
            throw new ApiException("INVALID_SCHEDULE",
                    "registrationCloseAt must be before or equal to scheduleStart.", HttpStatus.BAD_REQUEST);
        }
    }
}
