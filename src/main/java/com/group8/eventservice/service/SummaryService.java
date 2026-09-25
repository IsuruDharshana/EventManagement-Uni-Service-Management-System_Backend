package com.group8.eventservice.service;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.group8.eventservice.dto.response.EventRegistrationSummary;
import com.group8.eventservice.dto.response.EventsOverview;
import com.group8.eventservice.entity.Event;
import com.group8.eventservice.entity.EventStatus;
import com.group8.eventservice.entity.RegistrationStatus;
import com.group8.eventservice.exception.ApiException;
import com.group8.eventservice.repository.EventRepository;
import com.group8.eventservice.repository.RegistrationRepository;
import com.group8.eventservice.security.SecurityUtils;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;

/** Participation numbers for organizers and admin staff. */
@Service
@RequiredArgsConstructor
public class SummaryService {

    private static final String ADMIN_STAFF = "ADMIN_STAFF";
    private static final String ORGANIZER = "ORGANIZER";

    private final EventRepository eventRepository;
    private final RegistrationRepository registrationRepository;

    public EventRegistrationSummary summaryForEvent(UUID eventId) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new EntityNotFoundException("Event " + eventId + " not found"));
        requireOwnerOrAdmin(event);

        long confirmed = registrationRepository.countByEvent_IdAndStatus(eventId, RegistrationStatus.CONFIRMED);
        long cancelled = registrationRepository.countByEvent_IdAndStatus(eventId, RegistrationStatus.CANCELLED);
        long remaining = Math.max(0, event.getCapacity() - confirmed);

        return new EventRegistrationSummary(event.getId(), event.getTitle(), event.getStatus(),
                event.getCapacity(), confirmed, cancelled, remaining);
    }

    public EventsOverview overview() {
        long draft = eventRepository.countByStatus(EventStatus.DRAFT);
        long published = eventRepository.countByStatus(EventStatus.PUBLISHED);
        long cancelled = eventRepository.countByStatus(EventStatus.CANCELLED);
        long completed = eventRepository.countByStatus(EventStatus.COMPLETED);

        return new EventsOverview(draft + published + cancelled + completed, draft, published, cancelled, completed,
                registrationRepository.countByStatus(RegistrationStatus.CONFIRMED),
                registrationRepository.countByStatus(RegistrationStatus.CANCELLED));
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
}
