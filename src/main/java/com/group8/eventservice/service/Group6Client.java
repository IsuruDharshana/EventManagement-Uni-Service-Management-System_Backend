package com.group8.eventservice.service;

import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Checks whether a venue is available for a proposed event schedule. Optional per the
 * backend guide — event creation should not hard-fail if Group 6 is unreachable, it just
 * can't confirm the venue. Same outage-handling pattern as {@link Group5Client}.
 */
@Service
public class Group6Client {

    private final RestClient restClient;
    private final boolean mock;

    public Group6Client(RestClient.Builder restClientBuilder,
                         @Value("${integrations.group6.base-url}") String baseUrl,
                         @Value("${integrations.group6.mock}") boolean mock) {
        this.restClient = restClientBuilder.baseUrl(baseUrl).build();
        this.mock = mock;
    }

    public VenueResult checkAvailability(String venue, UUID eventId) {
        if (mock) {
            return VenueResult.available();
        }

        try {
            Boolean available = restClient.get()
                    .uri("/api/venues/availability?venue={venue}&eventId={eventId}", venue, eventId)
                    .retrieve()
                    .body(Boolean.class);
            return Boolean.TRUE.equals(available) ? VenueResult.available() : VenueResult.occupied();
        } catch (RestClientException ex) {
            return VenueResult.unavailable();
        }
    }
}
