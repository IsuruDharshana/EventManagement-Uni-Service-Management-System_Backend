package com.group8.eventservice.service;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Marks published events as COMPLETED once their end time has passed, so feedback can be given
 * for them. Runs every events.auto-complete.interval (default 5 minutes); switch it off with
 * events.auto-complete.enabled=false.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "events.auto-complete.enabled", havingValue = "true", matchIfMissing = true)
public class EventCompletionJob {

    private final EventService eventService;

    @Scheduled(initialDelayString = "PT30S", fixedDelayString = "${events.auto-complete.interval:PT5M}")
    public void completeFinishedEvents() {
        try {
            int completed = eventService.completeFinishedEvents();
            if (completed > 0) {
                log.info("Marked {} finished event(s) as COMPLETED", completed);
            }
        } catch (RuntimeException ex) {
            log.warn("Could not complete finished events, will retry on the next run: {}", ex.getMessage());
        }
    }
}
