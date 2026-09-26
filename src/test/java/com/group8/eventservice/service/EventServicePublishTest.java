package com.group8.eventservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
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
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import com.group8.eventservice.entity.Event;
import com.group8.eventservice.entity.EventStatus;
import com.group8.eventservice.exception.ApiException;
import com.group8.eventservice.repository.EventRepository;
import com.group8.eventservice.repository.RegistrationRepository;

class EventServicePublishTest {

    private EventRepository eventRepository;
    private Group6Client group6Client;
    private EventService service;
    private UUID eventId;

    @BeforeEach
    void setUp() {
        eventRepository = mock(EventRepository.class);
        group6Client = mock(Group6Client.class);
        service = new EventService(eventRepository, mock(RegistrationRepository.class), group6Client,
                mock(ApplicationEventPublisher.class));
        eventId = UUID.randomUUID();

        var admin = new UsernamePasswordAuthenticationToken(
                "usr-admin-001", null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
        SecurityContextHolder.getContext().setAuthentication(admin);

        when(eventRepository.saveAndFlush(any(Event.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private Event draft(boolean online, String venue) {
        Event event = Event.builder()
                .id(eventId)
                .status(EventStatus.DRAFT)
                .online(online)
                .venue(venue)
                .organizerId("usr-organizer-001")
                .scheduleStart(LocalDateTime.now().plusDays(7))
                .scheduleEnd(LocalDateTime.now().plusDays(7).plusHours(2))
                .build();
        when(eventRepository.findById(eventId)).thenReturn(Optional.of(event));
        return event;
    }

    @Test
    void publishesPhysicalEventWhenVenueIsValid() {
        draft(false, "LAB-101");
        when(group6Client.validateVenue("LAB-101")).thenReturn(VenueResult.valid());

        assertThat(service.publishEvent(eventId).status()).isEqualTo(EventStatus.PUBLISHED);
    }

    @Test
    void rejectsPublishWhenVenueIsNotAvailableAndShowsGroup6Message() {
        Event event = draft(false, "LAB-101");
        when(group6Client.validateVenue("LAB-101"))
                .thenReturn(VenueResult.notAvailable("Resource is currently marked unavailable"));

        assertThatThrownBy(() -> service.publishEvent(eventId))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> {
                    assertThat(((ApiException) ex).getCode()).isEqualTo("VENUE_NOT_AVAILABLE");
                    assertThat(ex.getMessage()).isEqualTo("Resource is currently marked unavailable");
                });
        assertThat(event.getStatus()).isEqualTo(EventStatus.DRAFT);
    }

    @Test
    void rejectsPublishWhenVenueDoesNotExistAndShowsGroup6Message() {
        Event event = draft(false, "LAB-999");
        when(group6Client.validateVenue("LAB-999"))
                .thenReturn(VenueResult.notFound("Resource with ID 999 does not exist"));

        assertThatThrownBy(() -> service.publishEvent(eventId))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> {
                    assertThat(((ApiException) ex).getCode()).isEqualTo("VENUE_NOT_FOUND");
                    assertThat(ex.getMessage()).isEqualTo("Resource with ID 999 does not exist");
                });
        assertThat(event.getStatus()).isEqualTo(EventStatus.DRAFT);
    }

    @Test
    void rejectsPublishWhenGroup6IsUnavailableAndLeavesEventInDraft() {
        Event event = draft(false, "LAB-101");
        when(group6Client.validateVenue("LAB-101")).thenReturn(VenueResult.serviceUnavailable());

        assertThatThrownBy(() -> service.publishEvent(eventId))
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).getCode())
                .isEqualTo("GROUP6_UNAVAILABLE");
        assertThat(event.getStatus()).isEqualTo(EventStatus.DRAFT);
        verify(eventRepository, never()).saveAndFlush(any());
    }

    @Test
    void skipsVenueCheckForOnlineEvents() {
        draft(true, null);

        assertThat(service.publishEvent(eventId).status()).isEqualTo(EventStatus.PUBLISHED);
        verify(group6Client, never()).validateVenue(anyString());
    }
}
