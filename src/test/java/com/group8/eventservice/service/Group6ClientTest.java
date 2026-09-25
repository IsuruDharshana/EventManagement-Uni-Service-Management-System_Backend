package com.group8.eventservice.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

class Group6ClientTest {

    @Test
    void mockModeReturnsValidWithoutCallingOut() {
        Group6Client client = new Group6Client(RestClient.builder(), "http://localhost:1", true);

        assertThat(client.validateVenue("LAB-101").status()).isEqualTo(VenueResult.Status.VALID);
    }

    @Test
    void unreachableServiceReturnsServiceUnavailableInsteadOfThrowing() {
        Group6Client client = new Group6Client(RestClient.builder(), "http://localhost:1", false);

        VenueResult result = client.validateVenue("LAB-101");

        assertThat(result.status()).isEqualTo(VenueResult.Status.SERVICE_UNAVAILABLE);
        assertThat(result.isServiceAvailable()).isFalse();
    }
}
