package com.group8.eventservice.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

class Group5ClientTest {

    private static final EligibilityRule CS_STUDENTS =
            EligibilityRule.parse("{\"roles\": [\"STUDENT\"], \"departmentId\": \"CS\"}");

    private static Group5Client client(boolean mock) {
        // Port 1 is a privileged port nothing listens on — any real call is refused.
        return new Group5Client(RestClient.builder(), "http://localhost:1", mock, Duration.ofMillis(500), Duration.ofMillis(500));
    }

    @Test
    void mockModeReturnsEligibleWithoutCallingOut() {
        EligibilityResult result = client(true).checkEligibility("usr-student-001", CS_STUDENTS, "token");

        assertThat(result.status()).isEqualTo(EligibilityResult.Status.ELIGIBLE);
        assertThat(result.isEligible()).isTrue();
    }

    @Test
    void openToAllEventsDoNotCallGroup5() {
        EligibilityResult result = client(false).checkEligibility("usr-student-001", EligibilityRule.openToAll(), "token");

        assertThat(result.status()).isEqualTo(EligibilityResult.Status.ELIGIBLE);
    }

    @Test
    void unreachableServiceReturnsUnavailableInsteadOfThrowing() {
        EligibilityResult result = client(false).checkEligibility("usr-student-001", CS_STUDENTS, "token");

        assertThat(result.status()).isEqualTo(EligibilityResult.Status.UNAVAILABLE);
        assertThat(result.isAvailable()).isFalse();
    }
}
