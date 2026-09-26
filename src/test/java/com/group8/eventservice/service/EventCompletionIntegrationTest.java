package com.group8.eventservice.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import com.group8.eventservice.entity.Event;
import com.group8.eventservice.entity.EventStatus;
import com.group8.eventservice.repository.EventRepository;

/**
 * Runs the bulk "complete finished events" update against the real MySQL schema (same database
 * setup as EventServiceApplicationTests). Rolled back after the test.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=${DB_URL:jdbc:mysql://localhost:3307/event_service_db}",
        "spring.datasource.username=${DB_USERNAME:group8}",
        "spring.datasource.password=${DB_PASSWORD:group8}",
        "events.auto-complete.enabled=false"
})
@Transactional
class EventCompletionIntegrationTest {

    @Autowired
    private EventRepository eventRepository;

    @Autowired
    private EventService eventService;

    private Event save(EventStatus status, LocalDateTime end) {
        return eventRepository.saveAndFlush(Event.builder()
                .title("Completion test " + status)
                .organizerId("usr-organizer-001")
                .online(true)
                .scheduleStart(end.minusHours(2))
                .scheduleEnd(end)
                .capacity(10)
                .eligibilityRule("{\"all\": true}")
                .registrationOpenAt(end.minusDays(5))
                .registrationCloseAt(end.minusHours(2))
                .status(status)
                .build());
    }

    private EventStatus statusOf(Event event) {
        return eventRepository.findById(event.getId()).orElseThrow().getStatus();
    }

    @Test
    void completesOnlyPublishedEventsThatHaveEnded() {
        LocalDateTime now = LocalDateTime.now();
        Event ended = save(EventStatus.PUBLISHED, now.minusHours(1));
        Event running = save(EventStatus.PUBLISHED, now.plusHours(1));
        Event endedDraft = save(EventStatus.DRAFT, now.minusHours(1));
        Event endedCancelled = save(EventStatus.CANCELLED, now.minusHours(1));

        assertThat(eventService.completeFinishedEvents()).isGreaterThanOrEqualTo(1);

        assertThat(statusOf(ended)).isEqualTo(EventStatus.COMPLETED);
        assertThat(statusOf(running)).isEqualTo(EventStatus.PUBLISHED);
        assertThat(statusOf(endedDraft)).isEqualTo(EventStatus.DRAFT);
        assertThat(statusOf(endedCancelled)).isEqualTo(EventStatus.CANCELLED);
        assertThat(eventService.completeFinishedEvents()).isZero();
    }
}
