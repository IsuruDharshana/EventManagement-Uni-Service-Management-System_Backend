package com.group8.eventservice.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

class Group6ClientTest {

    @Test
    void mockModeReturnsValidWithoutCallingOut() {
        Group6Client client = new Group6Client(RestClient.builder(), "http://localhost:1", true, Duration.ofSeconds(1), Duration.ofSeconds(1));

        assertThat(client.validateVenue("LAB-101", null).status()).isEqualTo(VenueResult.Status.VALID);
    }

    @Test
    void unreachableServiceReturnsServiceUnavailableInsteadOfThrowing() {
        Group6Client client = new Group6Client(RestClient.builder(), "http://localhost:1", false, Duration.ofSeconds(1), Duration.ofSeconds(1));

        VenueResult result = client.validateVenue("LAB-101", null);

        assertThat(result.status()).isEqualTo(VenueResult.Status.SERVICE_UNAVAILABLE);
        assertThat(result.isServiceAvailable()).isFalse();
    }
}
