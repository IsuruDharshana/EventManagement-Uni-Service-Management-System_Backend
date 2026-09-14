package com.group8.eventservice.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

class Group6ClientTest {

    @Test
    void mockModeReturnsAvailableWithoutCallingOut() {
        Group6Client client = new Group6Client(RestClient.builder(), "http://localhost:1", true);

        VenueResult result = client.checkAvailability("Main Auditorium", UUID.randomUUID());

        assertThat(result.status()).isEqualTo(VenueResult.Status.AVAILABLE);
        assertThat(result.isAvailable()).isTrue();
    }

    @Test
    void unreachableServiceReturnsUnavailableInsteadOfThrowing() {
        Group6Client client = new Group6Client(RestClient.builder(), "http://localhost:1", false);

        VenueResult result = client.checkAvailability("Main Auditorium", UUID.randomUUID());

        assertThat(result.status()).isEqualTo(VenueResult.Status.UNAVAILABLE);
        assertThat(result.isServiceAvailable()).isFalse();
    }
}
