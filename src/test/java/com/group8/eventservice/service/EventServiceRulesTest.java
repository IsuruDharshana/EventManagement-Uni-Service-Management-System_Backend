package com.group8.eventservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
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
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import com.group8.eventservice.dto.request.CreateEventRequest;
import com.group8.eventservice.dto.request.UpdateEventRequest;
import com.group8.eventservice.entity.Event;
import com.group8.eventservice.entity.EventStatus;
import com.group8.eventservice.exception.ApiException;
import com.group8.eventservice.entity.RegistrationStatus;
import com.group8.eventservice.notification.NotificationRequest;
import com.group8.eventservice.notification.NotificationsRequested;
import com.group8.eventservice.repository.EventRepository;
import com.group8.eventservice.repository.RegistrationRepository;

/** Ownership, status-transition and schedule rules for events (S2-01.2, S2-01.3). */
class EventServiceRulesTest {

    private EventRepository eventRepository;
    private RegistrationRepository registrationRepository;
    private ApplicationEventPublisher eventPublisher;
    private EventService service;
    private UUID eventId;
    private String ownerId;

    @BeforeEach
    void setUp() {
        eventRepository = mock(EventRepository.class);
        registrationRepository = mock(RegistrationRepository.class);
        eventPublisher = mock(ApplicationEventPublisher.class);
        service = new EventService(eventRepository, registrationRepository, mock(Group6Client.class), eventPublisher);
        eventId = UUID.randomUUID();
        ownerId = "usr-organizer-001";
        when(eventRepository.saveAndFlush(any(Event.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private void loginAs(String userId, String role) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                userId, null, List.of(new SimpleGrantedAuthority("ROLE_" + role))));
    }

    private Event event(EventStatus status) {
        LocalDateTime now = LocalDateTime.now();
        Event event = Event.builder()
                .id(eventId)
                .title("Original")
                .organizerId(ownerId)
                .online(true)
                .status(status)
                .capacity(10)
                .scheduleStart(now.plusDays(7))
                .scheduleEnd(now.plusDays(7).plusHours(2))
                .registrationOpenAt(now.minusDays(1))
                .registrationCloseAt(now.plusDays(6))
                .build();
        when(eventRepository.findById(eventId)).thenReturn(Optional.of(event));
        return event;
    }

    private static String codeOf(Throwable t) {
        return ((ApiException) t).getCode();
    }

    @Test
    void ownerCanUpdateOwnEvent() {
        event(EventStatus.DRAFT);
        loginAs(ownerId, "EVENT_ORGANIZER");
        UpdateEventRequest request = new UpdateEventRequest();
        request.setTitle("Renamed");

        assertThat(service.updateEvent(eventId, request).title()).isEqualTo("Renamed");
    }

    @Test
    void adminCanUpdateAnyEvent() {
        event(EventStatus.DRAFT);
        loginAs("usr-admin-001", "ADMIN");
        UpdateEventRequest request = new UpdateEventRequest();
        request.setCapacity(99);

        assertThat(service.updateEvent(eventId, request).capacity()).isEqualTo(99);
    }

    @Test
    void anotherOrganizerCannotUpdate() {
        event(EventStatus.DRAFT);
        loginAs("usr-organizer-002", "EVENT_ORGANIZER");

        assertThatThrownBy(() -> service.updateEvent(eventId, new UpdateEventRequest()))
                .isInstanceOf(ApiException.class).extracting(EventServiceRulesTest::codeOf).isEqualTo("FORBIDDEN");
    }

    @Test
    void anotherOrganizerCannotPublishOrCancel() {
        event(EventStatus.DRAFT);
        loginAs("usr-organizer-002", "EVENT_ORGANIZER");

        assertThatThrownBy(() -> service.publishEvent(eventId)).isInstanceOf(ApiException.class)
                .extracting(EventServiceRulesTest::codeOf).isEqualTo("FORBIDDEN");
        assertThatThrownBy(() -> service.cancelEvent(eventId)).isInstanceOf(ApiException.class)
                .extracting(EventServiceRulesTest::codeOf).isEqualTo("FORBIDDEN");
    }

    @Test
    void cannotPublishAnEventThatIsNotDraft() {
        event(EventStatus.PUBLISHED);
        loginAs(ownerId, "EVENT_ORGANIZER");

        assertThatThrownBy(() -> service.publishEvent(eventId)).isInstanceOf(ApiException.class)
                .extracting(EventServiceRulesTest::codeOf).isEqualTo("INVALID_STATE");
    }

    @Test
    void cannotCancelAnEventThatIsAlreadyCancelledOrCompleted() {
        loginAs(ownerId, "EVENT_ORGANIZER");
        for (EventStatus finished : List.of(EventStatus.CANCELLED, EventStatus.COMPLETED)) {
            event(finished);
            assertThatThrownBy(() -> service.cancelEvent(eventId)).isInstanceOf(ApiException.class)
                    .extracting(EventServiceRulesTest::codeOf).isEqualTo("INVALID_STATE");
        }
    }

    @Test
    void publishedEventCanBeCancelled() {
        event(EventStatus.PUBLISHED);
        loginAs(ownerId, "EVENT_ORGANIZER");

        assertThat(service.cancelEvent(eventId).status()).isEqualTo(EventStatus.CANCELLED);
    }

    @Test
    void updateRejectsEndBeforeStart() {
        Event event = event(EventStatus.DRAFT);
        loginAs(ownerId, "EVENT_ORGANIZER");
        UpdateEventRequest request = new UpdateEventRequest();
        request.setScheduleEnd(event.getScheduleStart().minusHours(1));

        assertThatThrownBy(() -> service.updateEvent(eventId, request)).isInstanceOf(ApiException.class)
                .extracting(EventServiceRulesTest::codeOf).isEqualTo("INVALID_SCHEDULE");
    }

    @Test
    void updateRejectsEndEqualToStart() {
        Event event = event(EventStatus.DRAFT);
        loginAs(ownerId, "EVENT_ORGANIZER");
        UpdateEventRequest request = new UpdateEventRequest();
        request.setScheduleEnd(event.getScheduleStart());

        assertThatThrownBy(() -> service.updateEvent(eventId, request)).isInstanceOf(ApiException.class)
                .extracting(EventServiceRulesTest::codeOf).isEqualTo("INVALID_SCHEDULE");
    }

    @Test
    void updateRejectsRegistrationClosingAfterEventStarts() {
        Event event = event(EventStatus.DRAFT);
        loginAs(ownerId, "EVENT_ORGANIZER");
        UpdateEventRequest request = new UpdateEventRequest();
        request.setRegistrationCloseAt(event.getScheduleStart().plusMinutes(1));

        assertThatThrownBy(() -> service.updateEvent(eventId, request)).isInstanceOf(ApiException.class)
                .extracting(EventServiceRulesTest::codeOf).isEqualTo("INVALID_SCHEDULE");
    }

    @Test
    void updateAllowsRegistrationClosingExactlyAtStart() {
        Event event = event(EventStatus.DRAFT);
        loginAs(ownerId, "EVENT_ORGANIZER");
        UpdateEventRequest request = new UpdateEventRequest();
        request.setRegistrationCloseAt(event.getScheduleStart());

        assertThat(service.updateEvent(eventId, request).registrationCloseAt()).isEqualTo(event.getScheduleStart());
    }

    @Test
    void studentCannotSeeSomeoneElsesDraft() {
        event(EventStatus.DRAFT);
        loginAs("usr-student-001", "STUDENT");

        assertThatThrownBy(() -> service.getEventDetail(eventId)).isInstanceOf(ApiException.class)
                .extracting(EventServiceRulesTest::codeOf).isEqualTo("NOT_VISIBLE");
    }

    @Test
    void academicStaffOwnerCanUpdateOwnEvent() {
        event(EventStatus.DRAFT);
        loginAs(ownerId, "ACADEMIC_STAFF");
        UpdateEventRequest request = new UpdateEventRequest();
        request.setTitle("Renamed");

        assertThat(service.updateEvent(eventId, request).title()).isEqualTo("Renamed");
    }

    @Test
    void administrativeStaffCanSeeDraftsButNotEditThem() {
        event(EventStatus.DRAFT);
        loginAs("usr-staff-001", "ADMINISTRATIVE_STAFF");

        assertThat(service.getEventDetail(eventId).status()).isEqualTo(EventStatus.DRAFT);
        assertThatThrownBy(() -> service.updateEvent(eventId, new UpdateEventRequest()))
                .isInstanceOf(ApiException.class).extracting(EventServiceRulesTest::codeOf).isEqualTo("FORBIDDEN");
    }

    @Test
    void updateRejectsAnInvalidEligibilityRule() {
        event(EventStatus.DRAFT);
        loginAs(ownerId, "EVENT_ORGANIZER");
        UpdateEventRequest request = new UpdateEventRequest();
        request.setEligibilityRule("{\"department\": \"Computing\"}");

        assertThatThrownBy(() -> service.updateEvent(eventId, request)).isInstanceOf(ApiException.class)
                .extracting(EventServiceRulesTest::codeOf).isEqualTo("INVALID_ELIGIBILITY_RULE");
    }

    @Test
    void createRejectsAnInvalidEligibilityRuleAndSavesNothing() {
        loginAs(ownerId, "EVENT_ORGANIZER");
        CreateEventRequest request = new CreateEventRequest();
        request.setTitle("Workshop");
        request.setEligibilityRule("{}");

        assertThatThrownBy(() -> service.createEvent(request)).isInstanceOf(ApiException.class)
                .extracting(EventServiceRulesTest::codeOf).isEqualTo("INVALID_ELIGIBILITY_RULE");
        verify(eventRepository, never()).saveAndFlush(any());
    }

    @Test
    void createStoresTheCallerAsOrganizer() {
        loginAs(ownerId, "ACADEMIC_STAFF");
        LocalDateTime start = LocalDateTime.now().plusDays(7);
        CreateEventRequest request = new CreateEventRequest();
        request.setTitle("Workshop");
        request.setScheduleStart(start);
        request.setScheduleEnd(start.plusHours(2));
        request.setCapacity(10);
        request.setEligibilityRule("{\"roles\": [\"STUDENT\"], \"facultyId\": \"fac-sci\"}");
        request.setRegistrationOpenAt(LocalDateTime.now());
        request.setRegistrationCloseAt(start);

        assertThat(service.createEvent(request).organizerId()).isEqualTo(ownerId);
    }

    @Test
    void ownerCanCompleteAStartedEvent() {
        Event event = event(EventStatus.PUBLISHED);
        event.setScheduleStart(LocalDateTime.now().minusHours(1));
        loginAs(ownerId, "EVENT_ORGANIZER");

        assertThat(service.completeEvent(eventId).status()).isEqualTo(EventStatus.COMPLETED);
    }

    @Test
    void adminCanCompleteAnyStartedEvent() {
        Event event = event(EventStatus.PUBLISHED);
        event.setScheduleStart(LocalDateTime.now().minusHours(1));
        loginAs("usr-admin-001", "ADMIN");

        assertThat(service.completeEvent(eventId).status()).isEqualTo(EventStatus.COMPLETED);
    }

    @Test
    void cannotCompleteAnEventBeforeItStarts() {
        Event event = event(EventStatus.PUBLISHED);
        loginAs(ownerId, "EVENT_ORGANIZER");

        assertThatThrownBy(() -> service.completeEvent(eventId)).isInstanceOf(ApiException.class)
                .extracting(EventServiceRulesTest::codeOf).isEqualTo("EVENT_NOT_STARTED");
        assertThat(event.getStatus()).isEqualTo(EventStatus.PUBLISHED);
    }

    @Test
    void onlyPublishedEventsCanBeCompleted() {
        loginAs(ownerId, "EVENT_ORGANIZER");
        for (EventStatus status : List.of(EventStatus.DRAFT, EventStatus.CANCELLED, EventStatus.COMPLETED)) {
            Event event = event(status);
            event.setScheduleStart(LocalDateTime.now().minusHours(1));
            assertThatThrownBy(() -> service.completeEvent(eventId)).as(status.name()).isInstanceOf(ApiException.class)
                    .extracting(EventServiceRulesTest::codeOf).isEqualTo("INVALID_STATE");
        }
    }

    @Test
    void anotherOrganizerCannotComplete() {
        Event event = event(EventStatus.PUBLISHED);
        event.setScheduleStart(LocalDateTime.now().minusHours(1));
        loginAs("usr-organizer-002", "EVENT_ORGANIZER");

        assertThatThrownBy(() -> service.completeEvent(eventId)).isInstanceOf(ApiException.class)
                .extracting(EventServiceRulesTest::codeOf).isEqualTo("FORBIDDEN");
    }

    private NotificationsRequested publishedNotifications() {
        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(eventPublisher).publishEvent(captor.capture());
        return (NotificationsRequested) captor.getValue();
    }

    @Test
    void cancellingAnEventNotifiesEveryConfirmedRegistrant() {
        event(EventStatus.PUBLISHED);
        loginAs(ownerId, "EVENT_ORGANIZER");
        when(registrationRepository.findUserIdsByEventIdAndStatus(eventId, RegistrationStatus.CONFIRMED))
                .thenReturn(List.of("usr-student-001", "usr-student-002"));

        service.cancelEvent(eventId);

        List<NotificationRequest> sent = publishedNotifications().notifications();
        assertThat(sent).extracting(NotificationRequest::recipientId).containsExactly("usr-student-001", "usr-student-002");
        assertThat(sent).allSatisfy(n -> {
            assertThat(n.type()).isEqualTo("EVENT_CANCELLED");
            assertThat(n.message()).isEqualTo("Original has been cancelled.");
            assertThat(n.relatedId()).isEqualTo(eventId.toString());
        });
        assertThat(sent.get(0).idempotencyKey()).isEqualTo("EVENT_CANCELLED:" + eventId + ":usr-student-001");
    }

    @Test
    void movingAPublishedEventNotifiesRegistrants() {
        Event event = event(EventStatus.PUBLISHED);
        loginAs(ownerId, "EVENT_ORGANIZER");
        when(registrationRepository.findUserIdsByEventIdAndStatus(eventId, RegistrationStatus.CONFIRMED))
                .thenReturn(List.of("usr-student-001"));
        UpdateEventRequest request = new UpdateEventRequest();
        request.setScheduleEnd(event.getScheduleEnd().plusHours(1));

        service.updateEvent(eventId, request);

        NotificationRequest sent = publishedNotifications().notifications().get(0);
        assertThat(sent.type()).isEqualTo("EVENT_UPDATED");
        assertThat(sent.idempotencyKey()).startsWith("EVENT_UPDATED:" + eventId + ":").endsWith(":usr-student-001");
    }

    @Test
    void renamingAnEventOrEditingADraftSendsNothing() {
        event(EventStatus.PUBLISHED);
        loginAs(ownerId, "EVENT_ORGANIZER");
        UpdateEventRequest rename = new UpdateEventRequest();
        rename.setTitle("Renamed");
        service.updateEvent(eventId, rename);

        Event draft = event(EventStatus.DRAFT);
        UpdateEventRequest move = new UpdateEventRequest();
        move.setScheduleEnd(draft.getScheduleEnd().plusHours(1));
        service.updateEvent(eventId, move);

        verify(eventPublisher, never()).publishEvent(any(Object.class));
    }

    @Test
    void studentCanSeePublishedEvent() {
        event(EventStatus.PUBLISHED);
        loginAs("usr-student-001", "STUDENT");

        assertThat(service.getEventDetail(eventId).status()).isEqualTo(EventStatus.PUBLISHED);
    }
}
