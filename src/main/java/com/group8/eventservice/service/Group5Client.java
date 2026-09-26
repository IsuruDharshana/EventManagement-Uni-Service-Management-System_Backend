package com.group8.eventservice.service;

import java.net.ConnectException;
import java.util.Collections;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import lombok.extern.slf4j.Slf4j;

/**
 * Asks Group 5 (Identity Service) whether a user may register for an event, using
 * GET /api/v1/validation/users/{userId}/eligibility with the caller's own token forwarded.
 * The event's {@link EligibilityRule} becomes the query: required_role, plus
 * relationship=AFFILIATION with department_id / faculty_id when given. When the rule lists
 * several roles, Group 5 is asked once per role and the first "eligible" answer wins.
 * {@code {"all": true}} rules and integrations.group5.mock=true never call out.
 *
 * Group 5's answers map to: data.eligible=true → ELIGIBLE; false → INELIGIBLE with data.message;
 * 401/403/404 (unknown, deleted or inactive user) → INVALID_USER; anything else, including 503
 * DEPENDENCY_UNAVAILABLE, timeouts and unreadable replies → UNAVAILABLE, never eligible.
 * A refused connection is retried once; a slow reply is not, so users never wait twice the timeout.
 */
@Service
@Slf4j
public class Group5Client {

    private static final int MAX_ATTEMPTS = 2;
    private static final int MAX_MESSAGE_LENGTH = 200;

    private final RestClient restClient;
    private final boolean mock;

    public Group5Client(RestClient.Builder restClientBuilder,
                         @Value("${integrations.group5.base-url}") String baseUrl,
                         @Value("${integrations.group5.mock}") boolean mock) {
        this.restClient = restClientBuilder.baseUrl(baseUrl).build();
        this.mock = mock;
    }

    public EligibilityResult checkEligibility(String userId, EligibilityRule rule, String bearerToken) {
        if (rule.all() || mock) {
            return EligibilityResult.eligible();
        }

        List<String> roles = rule.roles().isEmpty() ? Collections.singletonList(null) : rule.roles();
        boolean unavailable = false;
        String reason = null;
        for (String role : roles) {
            EligibilityResult result = checkWithRetry(userId, role, rule, bearerToken);
            switch (result.status()) {
                case ELIGIBLE, INVALID_USER -> {
                    return result;
                }
                case UNAVAILABLE -> unavailable = true;
                case INELIGIBLE -> reason = result.message();
            }
        }
        return unavailable ? EligibilityResult.unavailable() : EligibilityResult.ineligible(reason);
    }

    private EligibilityResult checkWithRetry(String userId, String role, EligibilityRule rule, String bearerToken) {
        for (int attempt = 1; ; attempt++) {
            try {
                return check(userId, role, rule, bearerToken);
            } catch (ResourceAccessException ex) {
                if (attempt < MAX_ATTEMPTS && ex.getCause() instanceof ConnectException) {
                    log.info("Group 5 connection refused, retrying once");
                    continue;
                }
                log.warn("Group 5 eligibility check unavailable for user {}: {}", userId, ex.getMessage());
                return EligibilityResult.unavailable();
            }
        }
    }

    private EligibilityResult check(String userId, String role, EligibilityRule rule, String bearerToken) {
        try {
            Group5Response response = restClient.get()
                    .uri(builder -> {
                        builder.path("/api/v1/validation/users/{userId}/eligibility");
                        if (role != null) {
                            builder.queryParam("required_role", role);
                        }
                        if (rule.hasAffiliation()) {
                            builder.queryParam("relationship", "AFFILIATION");
                        }
                        if (rule.departmentId() != null) {
                            builder.queryParam("department_id", rule.departmentId());
                        }
                        if (rule.facultyId() != null) {
                            builder.queryParam("faculty_id", rule.facultyId());
                        }
                        return builder.build(userId);
                    })
                    .headers(headers -> {
                        if (bearerToken != null) {
                            headers.setBearerAuth(bearerToken);
                        }
                    })
                    .retrieve()
                    .body(Group5Response.class);

            if (response == null || !Boolean.TRUE.equals(response.success())
                    || response.data() == null || response.data().eligible() == null) {
                log.warn("Group 5 returned an unusable eligibility reply for user {}", userId);
                return EligibilityResult.unavailable();
            }
            if (response.data().eligible()) {
                return EligibilityResult.eligible();
            }
            return EligibilityResult.ineligible(clean(response.data().message()));
        } catch (HttpClientErrorException ex) {
            int status = ex.getStatusCode().value();
            if (status == 401 || status == 403 || status == 404) {
                log.info("Group 5 could not validate user {} (HTTP {})", userId, status);
                return EligibilityResult.invalidUser();
            }
            log.error("Group 5 rejected the eligibility request for user {} (HTTP {}): {}",
                    userId, status, ex.getResponseBodyAsString());
            return EligibilityResult.unavailable();
        } catch (ResourceAccessException ex) {
            throw ex;
        } catch (RestClientException ex) {
            log.warn("Group 5 eligibility check failed for user {}: {}", userId, ex.getMessage());
            return EligibilityResult.unavailable();
        }
    }

    private static String clean(String message) {
        if (message == null || message.isBlank()) {
            return null;
        }
        return message.length() > MAX_MESSAGE_LENGTH ? message.substring(0, MAX_MESSAGE_LENGTH) : message;
    }

    /** Only the fields event-service needs from Group 5's reply; anything else is ignored. */
    record Group5Response(Boolean success, Data data) {
        record Data(Boolean eligible, List<String> reasons, String message) {
        }
    }
}
