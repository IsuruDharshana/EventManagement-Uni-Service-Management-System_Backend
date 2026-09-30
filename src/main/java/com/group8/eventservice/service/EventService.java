package com.group8.eventservice.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.group8.eventservice.dto.request.CreateEventRequest;
import com.group8.eventservice.dto.request.EventFilter;
import com.group8.eventservice.dto.request.UpdateEventRequest;
import com.group8.eventservice.dto.response.EventResponse;
import com.group8.eventservice.entity.Event;
import com.group8.eventservice.entity.EventStatus;
import com.group8.eventservice.entity.RegistrationStatus;
import com.group8.eventservice.exception.ApiException;
import com.group8.eventservice.notification.Notifications;
import com.group8.eventservice.repository.EventRepository;
import com.group8.eventservice.repository.EventSpecifications;
import com.group8.eventservice.repository.RegistrationRepository;
import com.group8.eventservice.security.Roles;
import com.group8.eventservice.security.SecurityUtils;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class EventService {

    private final EventRepository eventRepository;
    private final RegistrationRepository registrationRepository;
    private final Group6Client group6Client;
    private final ApplicationEventPublisher eventPublisher;

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

    public static final int MAX_PAGE_SIZE = 100;
    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final Sort BY_START = Sort.by("scheduleStart", "id");

    /**
     * Events the caller may see, filtered in the database and ordered by start time. Without page/size
     * the whole filtered list is returned (the original behaviour); total is always the full match count.
     */
    public EventPage listVisibleEvents(EventFilter filter) {
        if (filter.from() != null && filter.to() != null && filter.to().isBefore(filter.from())) {
            throw new ApiException("VALIDATION_ERROR", "to must not be before from.", HttpStatus.BAD_REQUEST);
        }

        String me = SecurityUtils.currentUserId();
        List<Specification<Event>> conditions = new ArrayList<>();
        if (!canSeeAllEvents()) {
            conditions.add(EventSpecifications.visibleTo(me));
        }
        if (filter.status() != null) {
            conditions.add(EventSpecifications.hasStatus(filter.status()));
        }
        if (filter.mine()) {
            conditions.add(EventSpecifications.organizedBy(me));
        }
        if (filter.upcoming()) {
            conditions.add(EventSpecifications.startsAtOrAfter(LocalDateTime.now()));
        }
        if (filter.from() != null) {
            conditions.add(EventSpecifications.startsAtOrAfter(filter.from()));
        }
        if (filter.to() != null) {
            conditions.add(EventSpecifications.startsAtOrBefore(filter.to()));
        }
        Specification<Event> spec = Specification.allOf(conditions);

        if (filter.page() == null && filter.size() == null) {
            List<EventResponse> events = eventRepository.findAll(spec, BY_START).stream().map(EventResponse::from).toList();
            return new EventPage(events, events.size());
        }

        int page = filter.page() == null ? 0 : filter.page();
        int size = filter.size() == null ? DEFAULT_PAGE_SIZE : filter.size();
        if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) {
            throw new ApiException("VALIDATION_ERROR",
                    "page must be 0 or more and size between 1 and " + MAX_PAGE_SIZE + ".", HttpStatus.BAD_REQUEST);
        }
        Page<Event> result = eventRepository.findAll(spec, PageRequest.of(page, size, BY_START));
        return new EventPage(result.getContent().stream().map(EventResponse::from).toList(), result.getTotalElements());
    }

    public record EventPage(List<EventResponse> events, long total) {
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
        String oldWhen = whenAndWhere(event);

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

        Event saved = eventRepository.saveAndFlush(event);
        // Registrants only hear about changes to when or where a published event happens.
        if (saved.getStatus() == EventStatus.PUBLISHED && !oldWhen.equals(whenAndWhere(saved))) {
            eventPublisher.publishEvent(Notifications.eventUpdated(saved, confirmedRegistrants(saved)));
        }
        return EventResponse.from(saved);
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
        Event saved = eventRepository.saveAndFlush(event);
        eventPublisher.publishEvent(Notifications.eventCancelled(saved, confirmedRegistrants(saved)));
        return EventResponse.from(saved);
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

        VenueResult venue = group6Client.validateVenue(event.getVenue().trim(), SecurityUtils.currentBearerToken());
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

    private List<String> confirmedRegistrants(Event event) {
        return registrationRepository.findUserIdsByEventIdAndStatus(event.getId(), RegistrationStatus.CONFIRMED);
    }

    private static String whenAndWhere(Event event) {
        return event.getScheduleStart() + "|" + event.getScheduleEnd() + "|" + event.isOnline() + "|"
                + Objects.toString(event.getVenue(), "");
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
