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
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import com.group8.eventservice.entity.Event;
import com.group8.eventservice.entity.EventStatus;
import com.group8.eventservice.exception.ApiException;
import com.group8.eventservice.repository.EventRepository;

class EventServicePublishTest {

    private EventRepository eventRepository;
    private Group6Client group6Client;
    private EventService service;
    private UUID eventId;

    @BeforeEach
    void setUp() {
        eventRepository = mock(EventRepository.class);
        group6Client = mock(Group6Client.class);
        service = new EventService(eventRepository, group6Client);
        eventId = UUID.randomUUID();

        var admin = new UsernamePasswordAuthenticationToken(
                UUID.randomUUID().toString(), null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN_STAFF")));
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
                .organizerId(UUID.randomUUID())
                .scheduleStart(LocalDateTime.now().plusDays(7))
                .scheduleEnd(LocalDateTime.now().plusDays(7).plusHours(2))
                .build();
        when(eventRepository.findById(eventId)).thenReturn(Optional.of(event));
        return event;
    }

    @Test
    void publishesPhysicalEventWhenVenueIsAvailable() {
        draft(false, "Main Hall");
        when(group6Client.checkAvailability("Main Hall", eventId)).thenReturn(VenueResult.available());

        assertThat(service.publishEvent(eventId).status()).isEqualTo(EventStatus.PUBLISHED);
    }

    @Test
    void rejectsPublishWhenVenueIsOccupied() {
        Event event = draft(false, "Main Hall");
        when(group6Client.checkAvailability("Main Hall", eventId)).thenReturn(VenueResult.occupied());

        assertThatThrownBy(() -> service.publishEvent(eventId))
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).getCode())
                .isEqualTo("VENUE_NOT_AVAILABLE");
        assertThat(event.getStatus()).isEqualTo(EventStatus.DRAFT);
    }

    @Test
    void rejectsPublishWhenGroup6IsUnavailableAndLeavesEventInDraft() {
        Event event = draft(false, "Main Hall");
        when(group6Client.checkAvailability("Main Hall", eventId)).thenReturn(VenueResult.unavailable());

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
        verify(group6Client, never()).checkAvailability(anyString(), any());
    }
}
