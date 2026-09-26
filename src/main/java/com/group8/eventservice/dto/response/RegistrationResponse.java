package com.group8.eventservice.dto.response;

import java.time.LocalDateTime;
import java.util.UUID;

import com.group8.eventservice.entity.Registration;
import com.group8.eventservice.entity.RegistrationStatus;

import io.swagger.v3.oas.annotations.media.Schema;

public record RegistrationResponse(
        @Schema(example = "b0000000-0000-0000-0000-000000000001") UUID id,
        @Schema(example = "a0000000-0000-0000-0000-000000000001") UUID eventId,
        @Schema(description = "Group 5 user id of the registered user", example = "usr-student-001") String userId,
        @Schema(description = "CONFIRMED counts toward capacity; CANCELLED by the user; WAITLISTED is reserved and not used yet")
        RegistrationStatus status,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {

    public static RegistrationResponse from(Registration registration) {
        return new RegistrationResponse(
                registration.getId(),
                registration.getEvent().getId(),
                registration.getUserId(),
                registration.getStatus(),
                registration.getCreatedAt(),
                registration.getUpdatedAt());
    }
}
