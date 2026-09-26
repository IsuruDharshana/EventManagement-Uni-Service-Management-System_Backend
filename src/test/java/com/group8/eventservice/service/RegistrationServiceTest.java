package com.group8.eventservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
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

class RegistrationServiceTest {

    private EventRepository eventRepository;
    private RegistrationRepository registrationRepository;
    private Group5Client group5Client;
    private RegistrationService service;
    private String userId;
    private UUID eventId;

    @BeforeEach
    void setUp() {
        eventRepository = mock(EventRepository.class);
        registrationRepository = mock(RegistrationRepository.class);
        group5Client = mock(Group5Client.class);
        service = new RegistrationService(eventRepository, registrationRepository, group5Client);

        userId = "usr-student-001";
        eventId = UUID.randomUUID();

        var auth = new UsernamePasswordAuthenticationToken(
                userId, "caller-token", List.of(new SimpleGrantedAuthority("ROLE_STUDENT")));
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private Event publishedEvent() {
        LocalDateTime now = LocalDateTime.now();
        return Event.builder()
                .id(eventId)
                .status(EventStatus.PUBLISHED)
                .capacity(2)
                .scheduleStart(now.plusDays(7))
                .scheduleEnd(now.plusDays(7).plusHours(2))
                .registrationOpenAt(now.minusDays(1))
                .registrationCloseAt(now.plusDays(1))
                .eligibilityRule("{\"roles\": [\"STUDENT\"], \"departmentId\": \"dep-cs\"}")
                .build();
    }

    @Test
    void rejectsRegistrationWhenEventNotPublished() {
        Event draft = publishedEvent();
        draft.setStatus(EventStatus.DRAFT);
        when(eventRepository.findByIdForUpdate(eventId)).thenReturn(Optional.of(draft));

        assertThatThrownBy(() -> service.register(eventId))
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).getCode())
                .isEqualTo("EVENT_NOT_PUBLISHED");
    }

    @Test
    void rejectsRegistrationOutsideRegistrationWindow() {
        Event event = publishedEvent();
        event.setRegistrationCloseAt(LocalDateTime.now().minusHours(1));
        when(eventRepository.findByIdForUpdate(eventId)).thenReturn(Optional.of(event));

        assertThatThrownBy(() -> service.register(eventId))
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).getCode())
                .isEqualTo("REGISTRATION_CLOSED");
    }

    @Test
    void rejectsRegistrationWhenGroup5IsUnavailable() {
        when(eventRepository.findByIdForUpdate(eventId)).thenReturn(Optional.of(publishedEvent()));
        when(group5Client.checkEligibility(eq(userId), any(), eq("caller-token"))).thenReturn(EligibilityResult.unavailable());

        assertThatThrownBy(() -> service.register(eventId))
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).getCode())
                .isEqualTo("GROUP5_UNAVAILABLE");
    }

    @Test
    void rejectsRegistrationWhenGroup5DoesNotKnowTheUser() {
        when(eventRepository.findByIdForUpdate(eventId)).thenReturn(Optional.of(publishedEvent()));
        when(group5Client.checkEligibility(eq(userId), any(), eq("caller-token"))).thenReturn(EligibilityResult.invalidUser());

        assertThatThrownBy(() -> service.register(eventId))
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).getCode())
                .isEqualTo("INVALID_USER");
    }

    @Test
    void rejectsRegistrationWhenIneligible() {
        when(eventRepository.findByIdForUpdate(eventId)).thenReturn(Optional.of(publishedEvent()));
        when(group5Client.checkEligibility(eq(userId), any(), eq("caller-token"))).thenReturn(EligibilityResult.ineligible());

        assertThatThrownBy(() -> service.register(eventId))
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).getCode())
                .isEqualTo("NOT_ELIGIBLE");
    }

    @Test
    void notEligibleShowsGroup5sReason() {
        when(eventRepository.findByIdForUpdate(eventId)).thenReturn(Optional.of(publishedEvent()));
        when(group5Client.checkEligibility(eq(userId), any(), eq("caller-token")))
                .thenReturn(EligibilityResult.ineligible("User is not affiliated with the requested department/faculty."));

        assertThatThrownBy(() -> service.register(eventId))
                .isInstanceOf(ApiException.class)
                .hasMessage("User is not affiliated with the requested department/faculty.");
    }

    @Test
    void sendsTheEventsRuleAndCallerTokenToGroup5() {
        when(eventRepository.findByIdForUpdate(eventId)).thenReturn(Optional.of(publishedEvent()));
        when(group5Client.checkEligibility(eq(userId), any(), eq("caller-token"))).thenReturn(EligibilityResult.ineligible());

        assertThatThrownBy(() -> service.register(eventId)).isInstanceOf(ApiException.class);

        verify(group5Client).checkEligibility(userId,
                new EligibilityRule(false, List.of("STUDENT"), "dep-cs", null), "caller-token");
    }

    @Test
    void eventWithAnInvalidStoredRuleAdmitsNobody() {
        Event event = publishedEvent();
        event.setEligibilityRule("{\"department\": \"Computing\"}");
        when(eventRepository.findByIdForUpdate(eventId)).thenReturn(Optional.of(event));

        assertThatThrownBy(() -> service.register(eventId))
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).getCode())
                .isEqualTo("NOT_ELIGIBLE");
        verify(group5Client, never()).checkEligibility(any(), any(), any());
    }

    @Test
    void rejectsRegistrationWhenCapacityReached() {
        Event event = publishedEvent();
        when(eventRepository.findByIdForUpdate(eventId)).thenReturn(Optional.of(event));
        when(group5Client.checkEligibility(eq(userId), any(), eq("caller-token"))).thenReturn(EligibilityResult.eligible());
        when(registrationRepository.countByEvent_IdAndStatus(eventId, RegistrationStatus.CONFIRMED))
                .thenReturn((long) event.getCapacity());

        assertThatThrownBy(() -> service.register(eventId))
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).getCode())
                .isEqualTo("CAPACITY_REACHED");
    }

    @Test
    void succeedsWhenEligibleAndCapacityAvailable() {
        Event event = publishedEvent();
        when(eventRepository.findByIdForUpdate(eventId)).thenReturn(Optional.of(event));
        when(group5Client.checkEligibility(eq(userId), any(), eq("caller-token"))).thenReturn(EligibilityResult.eligible());
        when(registrationRepository.countByEvent_IdAndStatus(eventId, RegistrationStatus.CONFIRMED)).thenReturn(0L);
        when(registrationRepository.saveAndFlush(any(Registration.class))).thenAnswer(inv -> {
            Registration r = inv.getArgument(0);
            r.setId(UUID.randomUUID());
            r.setCreatedAt(LocalDateTime.now());
            r.setUpdatedAt(LocalDateTime.now());
            return r;
        });

        var response = service.register(eventId);

        assertThat(response.status()).isEqualTo(RegistrationStatus.CONFIRMED);
        assertThat(response.eventId()).isEqualTo(eventId);
        assertThat(response.userId()).isEqualTo(userId);
    }

    @Test
    void cancelRejectsWhenNotOwner() {
        Registration registration = Registration.builder()
                .id(UUID.randomUUID())
                .event(publishedEvent())
                .userId("usr-student-999")
                .status(RegistrationStatus.CONFIRMED)
                .build();
        when(registrationRepository.findById(registration.getId())).thenReturn(Optional.of(registration));

        assertThatThrownBy(() -> service.cancelRegistration(registration.getId()))
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).getCode())
                .isEqualTo("FORBIDDEN");
    }

    @Test
    void cancelRejectsWhenAlreadyCancelled() {
        Registration registration = Registration.builder()
                .id(UUID.randomUUID())
                .event(publishedEvent())
                .userId(userId)
                .status(RegistrationStatus.CANCELLED)
                .build();
        when(registrationRepository.findById(registration.getId())).thenReturn(Optional.of(registration));

        assertThatThrownBy(() -> service.cancelRegistration(registration.getId()))
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).getCode())
                .isEqualTo("INVALID_STATE");
    }

    @Test
    void cancelSucceedsForOwner() {
        Registration registration = Registration.builder()
                .id(UUID.randomUUID())
                .event(publishedEvent())
                .userId(userId)
                .status(RegistrationStatus.CONFIRMED)
                .build();
        when(registrationRepository.findById(registration.getId())).thenReturn(Optional.of(registration));
        when(registrationRepository.saveAndFlush(eq(registration))).thenReturn(registration);

        var response = service.cancelRegistration(registration.getId());

        assertThat(response.status()).isEqualTo(RegistrationStatus.CANCELLED);
    }
}
