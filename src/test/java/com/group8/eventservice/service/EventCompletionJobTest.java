package com.group8.eventservice.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;

class EventCompletionJobTest {

    @Test
    void completesFinishedEvents() {
        EventService eventService = mock(EventService.class);
        when(eventService.completeFinishedEvents()).thenReturn(2);

        new EventCompletionJob(eventService).completeFinishedEvents();

        verify(eventService).completeFinishedEvents();
    }

    @Test
    void aDatabaseErrorDoesNotStopTheScheduler() {
        EventService eventService = mock(EventService.class);
        when(eventService.completeFinishedEvents()).thenThrow(new DataAccessResourceFailureException("db down"));

        assertThatCode(() -> new EventCompletionJob(eventService).completeFinishedEvents()).doesNotThrowAnyException();
    }
}
