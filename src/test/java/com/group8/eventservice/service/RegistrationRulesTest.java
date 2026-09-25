package com.group8.eventservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
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
import com.group8.eventservice.entity.Registration;
import com.group8.eventservice.entity.RegistrationStatus;
import com.group8.eventservice.exception.ApiException;
import com.group8.eventservice.repository.EventRepository;
import com.group8.eventservice.repository.RegistrationRepository;

/** Registration window boundaries and cancellation deadline (S2-02.2, S2-02.4). */
class RegistrationRulesTest {

    private EventRepository eventRepository;
    private RegistrationRepository registrationRepository;
    private Group5Client group5Client;
    private RegistrationService service;
    private UUID userId;
    private UUID eventId;

    @BeforeEach
    void setUp() {
        eventRepository = mock(EventRepository.class);
        registrationRepository = mock(RegistrationRepository.class);
        group5Client = mock(Group5Client.class);
        service = new RegistrationService(eventRepository, registrationRepository, group5Client);
        userId = UUID.randomUUID();
        eventId = UUID.randomUUID();

        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                userId.toString(), null, List.of(new SimpleGrantedAuthority("ROLE_STUDENT"))));

        when(group5Client.checkEligibility(userId, eventId)).thenReturn(EligibilityResult.eligible());
        when(registrationRepository.countByEvent_IdAndStatus(eventId, RegistrationStatus.CONFIRMED)).thenReturn(0L);
        when(registrationRepository.saveAndFlush(any(Registration.class))).thenAnswer(inv -> {
            Registration r = inv.getArgument(0);
            r.setId(UUID.randomUUID());
            return r;
        });
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private void eventWithWindow(LocalDateTime opensAt, LocalDateTime closesAt) {
        Event event = Event.builder()
                .id(eventId)
                .status(EventStatus.PUBLISHED)
                .capacity(5)
                .scheduleStart(LocalDateTime.now().plusDays(7))
                .scheduleEnd(LocalDateTime.now().plusDays(7).plusHours(2))
                .registrationOpenAt(opensAt)
                .registrationCloseAt(closesAt)
                .build();
        when(eventRepository.findByIdForUpdate(eventId)).thenReturn(Optional.of(event));
    }

    private static String codeOf(Throwable t) {
        return ((ApiException) t).getCode();
    }

    @Test
    void rejectsRegistrationBeforeTheWindowOpens() {
        LocalDateTime now = LocalDateTime.now();
        eventWithWindow(now.plusMinutes(1), now.plusDays(1));

        assertThatThrownBy(() -> service.register(eventId)).isInstanceOf(ApiException.class)
                .extracting(RegistrationRulesTest::codeOf).isEqualTo("REGISTRATION_CLOSED");
    }

    @Test
    void rejectsRegistrationJustAfterTheWindowCloses() {
        LocalDateTime now = LocalDateTime.now();
        eventWithWindow(now.minusDays(1), now.minusSeconds(1));

        assertThatThrownBy(() -> service.register(eventId)).isInstanceOf(ApiException.class)
                .extracting(RegistrationRulesTest::codeOf).isEqualTo("REGISTRATION_CLOSED");
    }

    @Test
    void allowsRegistrationJustAfterTheWindowOpens() {
        LocalDateTime now = LocalDateTime.now();
        eventWithWindow(now.minusSeconds(1), now.plusDays(1));

        assertThat(service.register(eventId).status()).isEqualTo(RegistrationStatus.CONFIRMED);
    }

    @Test
    void allowsRegistrationJustBeforeTheWindowCloses() {
        LocalDateTime now = LocalDateTime.now();
        eventWithWindow(now.minusDays(1), now.plusMinutes(1));

        assertThat(service.register(eventId).status()).isEqualTo(RegistrationStatus.CONFIRMED);
    }

    @Test
    void allowsTheLastSeatAndRejectsTheNext() {
        LocalDateTime now = LocalDateTime.now();
        eventWithWindow(now.minusDays(1), now.plusDays(1));

        when(registrationRepository.countByEvent_IdAndStatus(eventId, RegistrationStatus.CONFIRMED)).thenReturn(4L);
        assertThat(service.register(eventId).status()).isEqualTo(RegistrationStatus.CONFIRMED);

        when(registrationRepository.countByEvent_IdAndStatus(eventId, RegistrationStatus.CONFIRMED)).thenReturn(5L);
        assertThatThrownBy(() -> service.register(eventId)).isInstanceOf(ApiException.class)
                .extracting(RegistrationRulesTest::codeOf).isEqualTo("CAPACITY_REACHED");
    }

    @Test
    void cannotCancelAfterTheEventHasStarted() {
        Event started = Event.builder().id(eventId).status(EventStatus.PUBLISHED)
                .scheduleStart(LocalDateTime.now().minusMinutes(5)).build();
        Registration registration = Registration.builder().id(UUID.randomUUID()).event(started)
                .userId(userId).status(RegistrationStatus.CONFIRMED).build();
        when(registrationRepository.findById(registration.getId())).thenReturn(Optional.of(registration));

        assertThatThrownBy(() -> service.cancelRegistration(registration.getId())).isInstanceOf(ApiException.class)
                .extracting(RegistrationRulesTest::codeOf).isEqualTo("CANCELLATION_CLOSED");
        assertThat(registration.getStatus()).isEqualTo(RegistrationStatus.CONFIRMED);
    }

    @Test
    void canCancelUntilTheEventStarts() {
        Event upcoming = Event.builder().id(eventId).status(EventStatus.PUBLISHED)
                .scheduleStart(LocalDateTime.now().plusMinutes(5)).build();
        Registration registration = Registration.builder().id(UUID.randomUUID()).event(upcoming)
                .userId(userId).status(RegistrationStatus.CONFIRMED).build();
        when(registrationRepository.findById(registration.getId())).thenReturn(Optional.of(registration));

        assertThat(service.cancelRegistration(registration.getId()).status()).isEqualTo(RegistrationStatus.CANCELLED);
    }
}
