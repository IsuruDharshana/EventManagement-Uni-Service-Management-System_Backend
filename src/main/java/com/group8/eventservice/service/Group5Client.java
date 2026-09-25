package com.group8.eventservice.service;

import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import lombok.extern.slf4j.Slf4j;

/**
 * Checks whether a user is eligible to register for an event. Group 5 (the identity/user
 * service) may not exist yet this sprint (A8-02) — set integrations.group5.mock=true to
 * return a canned response instead of calling out. Every outbound call is caught so a
 * timeout/5xx/connection error becomes {@link EligibilityResult.Status#UNAVAILABLE} rather
 * than an unchecked exception reaching the controller (FR8-19).
 */
@Service
@Slf4j
public class Group5Client {

    private final RestClient restClient;
    private final boolean mock;

    public Group5Client(RestClient.Builder restClientBuilder,
                         @Value("${integrations.group5.base-url}") String baseUrl,
                         @Value("${integrations.group5.mock}") boolean mock) {
        this.restClient = restClientBuilder.baseUrl(baseUrl).build();
        this.mock = mock;
    }

    public EligibilityResult checkEligibility(UUID userId, UUID eventId) {
        if (mock) {
            return EligibilityResult.eligible();
        }

        try {
            Boolean eligible = restClient.get()
                    .uri("/api/users/{userId}/eligibility?eventId={eventId}", userId, eventId)
                    .retrieve()
                    .body(Boolean.class);
            if (eligible == null) {
                log.warn("Group 5 returned an empty eligibility response for user {}", userId);
                return EligibilityResult.unavailable();
            }
            return eligible ? EligibilityResult.eligible() : EligibilityResult.ineligible();
        } catch (HttpClientErrorException.NotFound ex) {
            return EligibilityResult.invalidUser();
        } catch (RestClientException ex) {
            log.warn("Group 5 eligibility check unavailable for user {} event {}: {}", userId, eventId, ex.getMessage());
            return EligibilityResult.unavailable();
        }
    }
}
