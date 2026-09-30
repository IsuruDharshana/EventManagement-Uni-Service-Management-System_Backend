package com.group8.eventservice.service;

import java.net.ConnectException;
import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import lombok.extern.slf4j.Slf4j;

/**
 * Validates a venue with Group 6's facility-resource-service before a physical event is published.
 * The event's venue holds the Group 6 resource code (e.g. LAB-101), validated with
 * GET /api/resources/code/{code}/validate. Group 6 answers HTTP 200 for every venue, including
 * unknown ones, and says what it found in data.exists / data.validForReservation / data.message.
 *
 * The caller's token is forwarded: Group 6 accepts it, and the API Gateway (the default route to
 * Group 6) requires one. The gateway sleeps when idle on Render, hence the longer timeouts; a
 * refused connection is retried once. With integrations.group6.mock=true a canned "valid" reply is
 * returned instead. Any outage, timeout, error status or unreadable reply becomes
 * {@link VenueResult.Status#SERVICE_UNAVAILABLE}.
 */
@Service
@Slf4j
public class Group6Client {

    private static final int MAX_ATTEMPTS = 2;
    private static final int MAX_MESSAGE_LENGTH = 200;

    private final RestClient restClient;
    private final boolean mock;

    public Group6Client(RestClient.Builder restClientBuilder,
                        @Value("${integrations.group6.base-url}") String baseUrl,
                        @Value("${integrations.group6.mock}") boolean mock,
                        @Value("${integrations.group6.connect-timeout:PT10S}") Duration connectTimeout,
                        @Value("${integrations.group6.read-timeout:PT60S}") Duration readTimeout) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(connectTimeout);
        factory.setReadTimeout(readTimeout);
        this.restClient = restClientBuilder.clone().baseUrl(baseUrl).requestFactory(factory).build();
        this.mock = mock;
    }

    public VenueResult validateVenue(String venueCode, String bearerToken) {
        if (mock) {
            return VenueResult.valid();
        }

        for (int attempt = 1; ; attempt++) {
            try {
                return check(venueCode, bearerToken);
            } catch (ResourceAccessException ex) {
                if (attempt < MAX_ATTEMPTS && ex.getCause() instanceof ConnectException) {
                    log.info("Group 6 connection refused, retrying once");
                    continue;
                }
                log.warn("Group 6 venue validation unavailable: {}", ex.getMessage());
                return VenueResult.serviceUnavailable();
            } catch (RestClientException ex) {
                log.warn("Group 6 venue validation unavailable: {}", ex.getMessage());
                return VenueResult.serviceUnavailable();
            }
        }
    }

    private VenueResult check(String venueCode, String bearerToken) {
        Group6Response response = restClient.get()
                .uri("/api/resources/code/{code}/validate", venueCode)
                .headers(headers -> {
                    if (bearerToken != null) {
                        headers.setBearerAuth(bearerToken);
                    }
                })
                .retrieve()
                .body(Group6Response.class);

        if (response == null || !response.success() || response.data() == null) {
            log.warn("Group 6 returned an unusable venue validation reply");
            return VenueResult.serviceUnavailable();
        }

        Group6Response.Data data = response.data();
        if (!data.exists()) {
            return VenueResult.notFound(clean(data.message(), "This venue is not registered with Group 6."));
        }
        if (!data.validForReservation()) {
            return VenueResult.notAvailable(clean(data.message(), "This venue is not available."));
        }
        return VenueResult.valid();
    }

    private static String clean(String message, String fallback) {
        if (message == null || message.isBlank()) {
            return fallback;
        }
        return message.length() > MAX_MESSAGE_LENGTH ? message.substring(0, MAX_MESSAGE_LENGTH) : message;
    }

    /** Only the fields event-service needs from Group 6's reply; anything else is ignored. */
    record Group6Response(boolean success, Data data) {
        record Data(boolean exists, boolean validForReservation, String message) {
        }
    }
}
