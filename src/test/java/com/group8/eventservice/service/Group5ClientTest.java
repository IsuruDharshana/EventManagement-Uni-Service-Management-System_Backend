package com.group8.eventservice.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

class Group5ClientTest {

    private static final EligibilityRule CS_STUDENTS =
            EligibilityRule.parse("{\"roles\": [\"STUDENT\"], \"departmentId\": \"dep-cs\"}");

    @Test
    void mockModeReturnsEligibleWithoutCallingOut() {
        Group5Client client = new Group5Client(RestClient.builder(), "http://localhost:1", true);

        EligibilityResult result = client.checkEligibility("usr-student-001", CS_STUDENTS, "token");

        assertThat(result.status()).isEqualTo(EligibilityResult.Status.ELIGIBLE);
        assertThat(result.isEligible()).isTrue();
    }

    @Test
    void openToAllEventsDoNotCallGroup5() {
        // Nothing listens on port 1, so any call would come back UNAVAILABLE.
        Group5Client client = new Group5Client(RestClient.builder(), "http://localhost:1", false);

        EligibilityResult result = client.checkEligibility("usr-student-001", EligibilityRule.openToAll(), "token");

        assertThat(result.status()).isEqualTo(EligibilityResult.Status.ELIGIBLE);
    }

    @Test
    void unreachableServiceReturnsUnavailableInsteadOfThrowing() {
        // Port 1 is a privileged port nothing listens on — connection is refused (and retried once).
        Group5Client client = new Group5Client(RestClient.builder(), "http://localhost:1", false);

        EligibilityResult result = client.checkEligibility("usr-student-001", CS_STUDENTS, "token");

        assertThat(result.status()).isEqualTo(EligibilityResult.Status.UNAVAILABLE);
        assertThat(result.isAvailable()).isFalse();
    }
}
