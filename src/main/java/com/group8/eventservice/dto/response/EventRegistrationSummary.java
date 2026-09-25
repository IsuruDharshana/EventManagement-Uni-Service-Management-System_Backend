package com.group8.eventservice.dto.response;

import java.util.UUID;

import com.group8.eventservice.entity.EventStatus;

public record EventRegistrationSummary(
        UUID eventId,
        String title,
        EventStatus status,
        int capacity,
        long confirmed,
        long cancelled,
        long remainingSeats) {
}
