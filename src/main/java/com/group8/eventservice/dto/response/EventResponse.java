package com.group8.eventservice.dto.response;

import java.time.LocalDateTime;
import java.util.UUID;

import com.group8.eventservice.entity.Event;
import com.group8.eventservice.entity.EventStatus;

import io.swagger.v3.oas.annotations.media.Schema;

public record EventResponse(
        @Schema(example = "a0000000-0000-0000-0000-000000000001") UUID id,
        @Schema(example = "Innovation Week Workshop") String title,
        @Schema(example = "Hands-on workshop for Computing students", nullable = true) String description,
        @Schema(description = "Group 5 user id of the organizer", example = "usr-organizer-001") String organizerId,
        @Schema(description = "Group 6 resource code; null for online events", example = "LAB-101", nullable = true) String venue,
        @Schema(example = "false") boolean online,
        @Schema(example = "2026-10-05T10:00:00") LocalDateTime scheduleStart,
        @Schema(example = "2026-10-05T13:00:00") LocalDateTime scheduleEnd,
        @Schema(example = "30") Integer capacity,
        @Schema(description = "JSON rule as a string, e.g. {\"all\": true} or {\"roles\": [\"STUDENT\"], \"departmentId\": \"CS\"}",
                example = "{\"roles\": [\"STUDENT\"], \"departmentId\": \"CS\"}") String eligibilityRule,
        @Schema(example = "2026-09-25T08:00:00") LocalDateTime registrationOpenAt,
        @Schema(example = "2026-10-04T18:00:00") LocalDateTime registrationCloseAt,
        @Schema(description = "DRAFT -> PUBLISHED -> COMPLETED, or CANCELLED from DRAFT/PUBLISHED") EventStatus status,
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
