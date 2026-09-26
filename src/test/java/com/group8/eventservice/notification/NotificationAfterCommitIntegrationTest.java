package com.group8.eventservice.notification;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.after;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.group8.eventservice.entity.Event;
import com.group8.eventservice.entity.EventStatus;
import com.group8.eventservice.exception.ApiException;
import com.group8.eventservice.repository.EventRepository;
import com.group8.eventservice.repository.RegistrationRepository;
import com.group8.eventservice.service.RegistrationService;

/**
 * Full wiring against the real MySQL schema: a notification goes out on a background thread only
 * after the registration commits, and never for a registration that was rejected.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=${DB_URL:jdbc:mysql://localhost:3307/event_service_db}",
        "spring.datasource.username=${DB_USERNAME:group8}",
        "spring.datasource.password=${DB_PASSWORD:group8}",
        "events.auto-complete.enabled=false"
})
class NotificationAfterCommitIntegrationTest {

    @Autowired
    private RegistrationService registrationService;

    @Autowired
    private EventRepository eventRepository;

    @Autowired
    private RegistrationRepository registrationRepository;

    @MockitoBean
    private NotificationClient notificationClient;

    private final List<UUID> createdEvents = new ArrayList<>();

    @BeforeEach
    void loginAsStudent() {
        when(notificationClient.send(any())).thenReturn(true);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                "usr-notify-test-001", null, List.of(new SimpleGrantedAuthority("ROLE_STUDENT"))));
    }

    @AfterEach
    void cleanUp() {
        SecurityContextHolder.clearContext();
        for (UUID eventId : createdEvents) {
            registrationRepository.findAll().stream()
                    .filter(r -> r.getEvent().getId().equals(eventId))
                    .forEach(registrationRepository::delete);
            eventRepository.deleteById(eventId);
        }
    }

    private UUID openEvent(int capacity) {
        LocalDateTime now = LocalDateTime.now();
        Event event = eventRepository.saveAndFlush(Event.builder()
                .title("Notification Test Event")
                .organizerId("usr-organizer-001")
                .online(true)
                .scheduleStart(now.plusDays(3))
                .scheduleEnd(now.plusDays(3).plusHours(1))
                .capacity(capacity)
                .eligibilityRule("{\"all\": true}")
                .registrationOpenAt(now.minusDays(1))
                .registrationCloseAt(now.plusDays(2))
                .status(EventStatus.PUBLISHED)
                .build());
        createdEvents.add(event.getId());
        return event.getId();
    }

    @Test
    void confirmedRegistrationIsNotifiedAfterCommit() {
        UUID eventId = openEvent(5);

        var registration = registrationService.register(eventId);

        verify(notificationClient, timeout(3000)).send(argThat(n ->
                n.type().equals("REGISTRATION_CONFIRMED")
                        && n.recipientId().equals("usr-notify-test-001")
                        && n.idempotencyKey().equals("REGISTRATION_CONFIRMED:" + registration.id())));
    }

    @Test
    void rejectedRegistrationIsNeverNotified() {
        UUID eventId = openEvent(1);
        registrationService.register(eventId);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                "usr-notify-test-002", null, List.of(new SimpleGrantedAuthority("ROLE_STUDENT"))));

        assertThatThrownBy(() -> registrationService.register(eventId)).isInstanceOf(ApiException.class);

        verify(notificationClient, after(1000).never()).send(argThat(n -> n.recipientId().equals("usr-notify-test-002")));
        verify(notificationClient, never()).send(argThat(n -> n.recipientId().equals("usr-notify-test-002")));
    }
}
