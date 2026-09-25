package com.group8.eventservice.dto.response;

public record EventsOverview(
        long totalEvents,
        long draft,
        long published,
        long cancelled,
        long completed,
        long confirmedRegistrations,
        long cancelledRegistrations) {
}
