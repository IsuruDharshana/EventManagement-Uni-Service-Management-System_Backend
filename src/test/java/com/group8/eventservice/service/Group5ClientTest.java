package com.group8.eventservice.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

class Group5ClientTest {

    @Test
    void mockModeReturnsEligibleWithoutCallingOut() {
        Group5Client client = new Group5Client(RestClient.builder(), "http://localhost:1", true);

        EligibilityResult result = client.checkEligibility(UUID.randomUUID(), UUID.randomUUID());

        assertThat(result.status()).isEqualTo(EligibilityResult.Status.ELIGIBLE);
        assertThat(result.isEligible()).isTrue();
    }

    @Test
    void unreachableServiceReturnsUnavailableInsteadOfThrowing() {
        // Port 1 is a privileged port nothing listens on — connection is refused immediately.
        Group5Client client = new Group5Client(RestClient.builder(), "http://localhost:1", false);

        EligibilityResult result = client.checkEligibility(UUID.randomUUID(), UUID.randomUUID());

        assertThat(result.status()).isEqualTo(EligibilityResult.Status.UNAVAILABLE);
        assertThat(result.isAvailable()).isFalse();
    }
}
