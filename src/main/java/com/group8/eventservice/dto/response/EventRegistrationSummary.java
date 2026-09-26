package com.group8.eventservice.dto.response;

import java.util.UUID;

import com.group8.eventservice.entity.EventStatus;

import io.swagger.v3.oas.annotations.media.Schema;

public record EventRegistrationSummary(
        @Schema(example = "a0000000-0000-0000-0000-000000000001") UUID eventId,
        @Schema(example = "Innovation Week Workshop") String title,
        EventStatus status,
        @Schema(example = "30") int capacity,
        @Schema(description = "Registrations that hold a seat", example = "12") long confirmed,
        @Schema(example = "3") long cancelled,
        @Schema(description = "capacity - confirmed, never below 0", example = "18") long remainingSeats) {
}
