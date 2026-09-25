package com.group8.eventservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import com.group8.eventservice.entity.Event;
import com.group8.eventservice.entity.EventStatus;
import com.group8.eventservice.entity.RegistrationStatus;
import com.group8.eventservice.exception.ApiException;
import com.group8.eventservice.repository.EventRepository;
import com.group8.eventservice.repository.RegistrationRepository;

import jakarta.persistence.EntityNotFoundException;

class SummaryServiceTest {

    private EventRepository eventRepository;
    private RegistrationRepository registrationRepository;
    private SummaryService service;
    private UUID eventId;
    private UUID ownerId;

    @BeforeEach
    void setUp() {
        eventRepository = mock(EventRepository.class);
        registrationRepository = mock(RegistrationRepository.class);
        service = new SummaryService(eventRepository, registrationRepository);
        eventId = UUID.randomUUID();
        ownerId = UUID.randomUUID();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private void loginAs(UUID userId, String role) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                userId.toString(), null, List.of(new SimpleGrantedAuthority("ROLE_" + role))));
    }

    private void event(int capacity, long confirmed, long cancelled) {
        Event event = Event.builder().id(eventId).title("Workshop").organizerId(ownerId)
                .status(EventStatus.PUBLISHED).capacity(capacity).build();
        when(eventRepository.findById(eventId)).thenReturn(Optional.of(event));
        when(registrationRepository.countByEvent_IdAndStatus(eventId, RegistrationStatus.CONFIRMED)).thenReturn(confirmed);
        when(registrationRepository.countByEvent_IdAndStatus(eventId, RegistrationStatus.CANCELLED)).thenReturn(cancelled);
    }

    @Test
    void ownerSeesCountsAndRemainingSeats() {
        event(30, 12, 3);
        loginAs(ownerId, "ORGANIZER");

        var summary = service.summaryForEvent(eventId);

        assertThat(summary.confirmed()).isEqualTo(12);
        assertThat(summary.cancelled()).isEqualTo(3);
        assertThat(summary.remainingSeats()).isEqualTo(18);
    }

    @Test
    void fullEventHasZeroRemainingSeats() {
        event(2, 2, 0);
        loginAs(ownerId, "ORGANIZER");

        assertThat(service.summaryForEvent(eventId).remainingSeats()).isZero();
    }

    @Test
    void remainingSeatsNeverGoNegativeWhenCapacityWasLowered() {
        event(2, 5, 0);
        loginAs(ownerId, "ORGANIZER");

        assertThat(service.summaryForEvent(eventId).remainingSeats()).isZero();
    }

    @Test
    void adminSeesAnyEvent() {
        event(10, 1, 0);
        loginAs(UUID.randomUUID(), "ADMIN_STAFF");

        assertThat(service.summaryForEvent(eventId).confirmed()).isEqualTo(1);
    }

    @Test
    void anotherOrganizerIsForbidden() {
        event(10, 1, 0);
        loginAs(UUID.randomUUID(), "ORGANIZER");

        assertThatThrownBy(() -> service.summaryForEvent(eventId)).isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).getCode()).isEqualTo("FORBIDDEN");
    }

    @Test
    void unknownEventIsNotFound() {
        when(eventRepository.findById(eventId)).thenReturn(Optional.empty());
        loginAs(ownerId, "ORGANIZER");

        assertThatThrownBy(() -> service.summaryForEvent(eventId)).isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    void overviewAddsUpEveryStatus() {
        when(eventRepository.countByStatus(EventStatus.DRAFT)).thenReturn(1L);
        when(eventRepository.countByStatus(EventStatus.PUBLISHED)).thenReturn(3L);
        when(eventRepository.countByStatus(EventStatus.CANCELLED)).thenReturn(1L);
        when(eventRepository.countByStatus(EventStatus.COMPLETED)).thenReturn(2L);
        when(registrationRepository.countByStatus(RegistrationStatus.CONFIRMED)).thenReturn(40L);
        when(registrationRepository.countByStatus(RegistrationStatus.CANCELLED)).thenReturn(5L);

        var overview = service.overview();

        assertThat(overview.totalEvents()).isEqualTo(7);
        assertThat(overview.confirmedRegistrations()).isEqualTo(40);
        assertThat(overview.cancelledRegistrations()).isEqualTo(5);
    }
}
