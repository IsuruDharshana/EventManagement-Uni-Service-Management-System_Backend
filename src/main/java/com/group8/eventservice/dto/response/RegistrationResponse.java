package com.group8.eventservice.dto.response;

import java.time.LocalDateTime;
import java.util.UUID;

import com.group8.eventservice.entity.Registration;
import com.group8.eventservice.entity.RegistrationStatus;

public record RegistrationResponse(
        UUID id,
        UUID eventId,
        String userId,
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
