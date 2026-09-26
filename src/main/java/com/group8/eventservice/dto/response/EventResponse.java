package com.group8.eventservice.dto.response;

import java.time.LocalDateTime;
import java.util.UUID;

import com.group8.eventservice.entity.Event;
import com.group8.eventservice.entity.EventStatus;

public record EventResponse(
        UUID id,
        String title,
        String description,
        String organizerId,
        String venue,
        boolean online,
        LocalDateTime scheduleStart,
        LocalDateTime scheduleEnd,
        Integer capacity,
        String eligibilityRule,
        LocalDateTime registrationOpenAt,
        LocalDateTime registrationCloseAt,
        EventStatus status,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {

    public static EventResponse from(Event event) {
        return new EventResponse(
                event.getId(),
                event.getTitle(),
                event.getDescription(),
                event.getOrganizerId(),
                event.getVenue(),
                event.isOnline(),
                event.getScheduleStart(),
                event.getScheduleEnd(),
                event.getCapacity(),
                event.getEligibilityRule(),
                event.getRegistrationOpenAt(),
                event.getRegistrationCloseAt(),
                event.getStatus(),
                event.getCreatedAt(),
                event.getUpdatedAt());
    }
}
