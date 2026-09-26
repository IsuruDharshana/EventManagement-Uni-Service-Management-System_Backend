package com.group8.eventservice.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.group8.eventservice.dto.response.RegistrationResponse;
import com.group8.eventservice.entity.Event;
import com.group8.eventservice.entity.EventStatus;
import com.group8.eventservice.entity.Registration;
import com.group8.eventservice.entity.RegistrationStatus;
import com.group8.eventservice.exception.ApiException;
import com.group8.eventservice.notification.Notifications;
import com.group8.eventservice.repository.EventRepository;
import com.group8.eventservice.repository.RegistrationRepository;
import com.group8.eventservice.security.SecurityUtils;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class RegistrationService {

    private final EventRepository eventRepository;
    private final RegistrationRepository registrationRepository;
    private final Group5Client group5Client;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public RegistrationResponse register(UUID eventId) {
        String userId = SecurityUtils.currentUserId();

        // Locks the event row so a concurrent registration for the same event can't also
        // pass the capacity check before this transaction commits (DV8-01).
        Event event = eventRepository.findByIdForUpdate(eventId)
                .orElseThrow(() -> new EntityNotFoundException("Event " + eventId + " not found"));

        if (event.getStatus() != EventStatus.PUBLISHED) {
            throw new ApiException("EVENT_NOT_PUBLISHED", "This event is not open for registration.", HttpStatus.BAD_REQUEST);
        }

        LocalDateTime now = LocalDateTime.now();
        if (now.isBefore(event.getRegistrationOpenAt()) || now.isAfter(event.getRegistrationCloseAt())) {
            throw new ApiException("REGISTRATION_CLOSED", "Registration is not currently open for this event.", HttpStatus.BAD_REQUEST);
        }

        EligibilityResult eligibility = group5Client.checkEligibility(userId, eligibilityRuleOf(event),
                SecurityUtils.currentBearerToken());
        if (!eligibility.isAvailable()) {
            throw new ApiException("GROUP5_UNAVAILABLE",
                    "Eligibility check is temporarily unavailable. Please try again shortly.", HttpStatus.SERVICE_UNAVAILABLE);
        }
        if (eligibility.status() == EligibilityResult.Status.INVALID_USER) {
            throw new ApiException("INVALID_USER", "Your account could not be verified.", HttpStatus.FORBIDDEN);
        }
        if (!eligibility.isEligible()) {
            String message = eligibility.message() != null
                    ? eligibility.message() : "You are not eligible to register for this event.";
            throw new ApiException("NOT_ELIGIBLE", message, HttpStatus.FORBIDDEN);
        }

        long confirmedCount = registrationRepository.countByEvent_IdAndStatus(eventId, RegistrationStatus.CONFIRMED);
        if (confirmedCount >= event.getCapacity()) {
            throw new ApiException("CAPACITY_REACHED", "This event has reached its registration capacity.", HttpStatus.CONFLICT);
        }

        try {
            Registration registration = Registration.builder()
                    .event(event)
                    .userId(userId)
                    .status(RegistrationStatus.CONFIRMED)
                    .build();
            Registration saved = registrationRepository.saveAndFlush(registration);
            eventPublisher.publishEvent(Notifications.registrationConfirmed(saved));
            return RegistrationResponse.from(saved);
        } catch (DataIntegrityViolationException ex) {
            throw new ApiException("ALREADY_REGISTERED", "You have already registered for this event.", HttpStatus.CONFLICT);
        }
    }

    /** A rule that no longer parses (e.g. saved before the Group 5 format) admits nobody rather than everybody. */
    private EligibilityRule eligibilityRuleOf(Event event) {
        try {
            return EligibilityRule.parse(event.getEligibilityRule());
        } catch (IllegalArgumentException ex) {
            log.warn("Event {} has an invalid eligibility rule: {}", event.getId(), ex.getMessage());
            throw new ApiException("NOT_ELIGIBLE",
                    "This event's eligibility rule is invalid, so registration is closed. Please contact the organizer.",
                    HttpStatus.FORBIDDEN);
        }
    }

    @Transactional
    public RegistrationResponse cancelRegistration(UUID registrationId) {
        Registration registration = registrationRepository.findById(registrationId)
                .orElseThrow(() -> new EntityNotFoundException("Registration " + registrationId + " not found"));

        if (!registration.getUserId().equals(SecurityUtils.currentUserId())) {
            throw new ApiException("FORBIDDEN", "You do not own this registration.", HttpStatus.FORBIDDEN);
        }

        if (registration.getStatus() == RegistrationStatus.CANCELLED) {
            throw new ApiException("INVALID_STATE", "Registration is already cancelled.", HttpStatus.BAD_REQUEST);
        }

        if (LocalDateTime.now().isAfter(registration.getEvent().getScheduleStart())) {
            throw new ApiException("CANCELLATION_CLOSED", "Cannot cancel after the event has started.", HttpStatus.BAD_REQUEST);
        }

        registration.setStatus(RegistrationStatus.CANCELLED);
        Registration saved = registrationRepository.saveAndFlush(registration);
        eventPublisher.publishEvent(Notifications.registrationCancelled(saved));
        return RegistrationResponse.from(saved);
    }

    public List<RegistrationResponse> myRegistrations() {
        return registrationRepository.findByUserId(SecurityUtils.currentUserId()).stream()
                .map(RegistrationResponse::from)
                .toList();
    }
}
