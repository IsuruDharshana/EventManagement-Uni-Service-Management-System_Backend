package com.group8.eventservice.service;

import java.time.LocalDateTime;
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
import com.group8.eventservice.security.Roles;
import com.group8.eventservice.security.SecurityUtils;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class EventService {

    private final EventRepository eventRepository;
    private final Group6Client group6Client;

    @Transactional
    public EventResponse createEvent(CreateEventRequest request) {
        requireValidEligibilityRule(request.getEligibilityRule());

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
        List<Event> events = canSeeAllEvents()
                ? eventRepository.findAll()
                : eventRepository.findAll().stream()
                        .filter(this::isVisibleToNonAdmin)
                        .toList();

        return events.stream().map(EventResponse::from).toList();
    }

    public EventResponse getEventDetail(UUID id) {
        Event event = findOrThrow(id);

        if (!canSeeAllEvents() && !isVisibleToNonAdmin(event)) {
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
            requireValidEligibilityRule(request.getEligibilityRule());
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

    /** Marks a published event as finished, e.g. when it ended early. Feedback is only accepted for COMPLETED events. */
    @Transactional
    public EventResponse completeEvent(UUID id) {
        Event event = findOrThrow(id);
        requireOwnerOrAdmin(event);

        if (event.getStatus() != EventStatus.PUBLISHED) {
            throw new ApiException("INVALID_STATE", "Only published events can be completed.", HttpStatus.BAD_REQUEST);
        }
        if (LocalDateTime.now().isBefore(event.getScheduleStart())) {
            throw new ApiException("EVENT_NOT_STARTED", "An event cannot be completed before it starts.", HttpStatus.BAD_REQUEST);
        }

        event.setStatus(EventStatus.COMPLETED);
        return EventResponse.from(eventRepository.saveAndFlush(event));
    }

    /** Completes every published event whose end time has passed. Run on a schedule by {@link EventCompletionJob}. */
    @Transactional
    public int completeFinishedEvents() {
        return eventRepository.completeFinishedEvents(LocalDateTime.now());
    }

    private void requireVenueAvailable(Event event) {
        if (event.isOnline() || event.getVenue() == null || event.getVenue().isBlank()) {
            return;
        }

        VenueResult venue = group6Client.validateVenue(event.getVenue().trim());
        switch (venue.status()) {
            case VALID -> { }
            case SERVICE_UNAVAILABLE -> throw new ApiException("GROUP6_UNAVAILABLE",
                    "Venue check is temporarily unavailable. Please try publishing again shortly.", HttpStatus.SERVICE_UNAVAILABLE);
            case NOT_FOUND -> throw new ApiException("VENUE_NOT_FOUND", venue.message(), HttpStatus.BAD_REQUEST);
            case NOT_AVAILABLE -> throw new ApiException("VENUE_NOT_AVAILABLE", venue.message(), HttpStatus.CONFLICT);
        }
    }

    private Event findOrThrow(UUID id) {
        return eventRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Event " + id + " not found"));
    }

    /** ADMIN can manage any event; EVENT_ORGANIZER and ACADEMIC_STAFF only their own. */
    private void requireOwnerOrAdmin(Event event) {
        if (SecurityUtils.currentUserHasRole(Roles.ADMIN)) {
            return;
        }
        if (SecurityUtils.currentUserHasAnyRole(Roles.EVENT_ORGANIZER, Roles.ACADEMIC_STAFF)
                && event.getOrganizerId().equals(SecurityUtils.currentUserId())) {
            return;
        }
        throw new ApiException("FORBIDDEN", "You do not own this event.", HttpStatus.FORBIDDEN);
    }

    private static boolean canSeeAllEvents() {
        return SecurityUtils.currentUserHasAnyRole(Roles.ADMIN, Roles.ADMINISTRATIVE_STAFF);
    }

    private static void requireValidEligibilityRule(String rule) {
        try {
            EligibilityRule.parse(rule);
        } catch (IllegalArgumentException ex) {
            throw new ApiException("INVALID_ELIGIBILITY_RULE", ex.getMessage(), HttpStatus.BAD_REQUEST);
        }
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
