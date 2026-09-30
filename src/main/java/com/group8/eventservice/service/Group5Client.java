package com.group8.eventservice.service;

import java.net.ConnectException;
import java.time.Duration;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.extern.slf4j.Slf4j;

/**
 * Asks Group 5 (Identity Service) whether a user may register for an event, forwarding the
 * caller's own token. Always one call, as Group 5 recommends:
 * <ul>
 * <li>roles only: GET /api/v1/validation/users/{id} — allowed if is_valid and roles contains an allowed role;</li>
 * <li>department / faculty (with or without roles): GET /api/v1/validation/users/{id}/eligibility
 * ?relationship=AFFILIATION&amp;department_id=CS — allowed if eligible and, when roles are listed,
 * roles contains one of them.</li>
 * </ul>
 * {@code {"all": true}} rules and integrations.group5.mock=true never call out.
 *
 * 401/403/404 (unknown, deleted or inactive user) → INVALID_USER; anything else, including 503
 * DEPENDENCY_UNAVAILABLE, timeouts and unreadable replies → UNAVAILABLE, never eligible. Group 5
 * sleeps when idle and can take about a minute to answer the first call, hence its own (longer)
 * timeouts. A refused connection is retried once.
 */
@Service
@Slf4j
public class Group5Client {

    private static final int MAX_ATTEMPTS = 2;
    private static final int MAX_MESSAGE_LENGTH = 200;
    static final String NO_ALLOWED_ROLE = "You do not have a role that can register for this event.";

    private final RestClient restClient;
    private final boolean mock;

    public Group5Client(RestClient.Builder restClientBuilder,
                        @Value("${integrations.group5.base-url}") String baseUrl,
                        @Value("${integrations.group5.mock}") boolean mock,
                        @Value("${integrations.group5.connect-timeout:PT10S}") Duration connectTimeout,
                        @Value("${integrations.group5.read-timeout:PT60S}") Duration readTimeout) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(connectTimeout);
        factory.setReadTimeout(readTimeout);
        this.restClient = restClientBuilder.clone().baseUrl(baseUrl).requestFactory(factory).build();
        this.mock = mock;
    }

    public EligibilityResult checkEligibility(String userId, EligibilityRule rule, String bearerToken) {
        if (rule.all() || mock) {
            return EligibilityResult.eligible();
        }
        for (int attempt = 1; ; attempt++) {
            try {
                return rule.hasAffiliation() ? checkAffiliation(userId, rule, bearerToken) : checkRoles(userId, rule, bearerToken);
            } catch (ResourceAccessException ex) {
                if (attempt < MAX_ATTEMPTS && ex.getCause() instanceof ConnectException) {
                    log.info("Group 5 connection refused, retrying once");
                    continue;
                }
                log.warn("Group 5 eligibility check unavailable for user {}: {}", userId, ex.getMessage());
                return EligibilityResult.unavailable();
            } catch (HttpClientErrorException ex) {
                int status = ex.getStatusCode().value();
                if (status == 401 || status == 403 || status == 404) {
                    log.info("Group 5 could not validate user {} (HTTP {})", userId, status);
                    return EligibilityResult.invalidUser();
                }
                log.error("Group 5 rejected the eligibility request for user {} (HTTP {}): {}",
                        userId, status, ex.getResponseBodyAsString());
                return EligibilityResult.unavailable();
            } catch (RestClientException ex) {
                log.warn("Group 5 eligibility check failed for user {}: {}", userId, ex.getMessage());
                return EligibilityResult.unavailable();
            }
        }
    }

    /** Roles only: no Directory lookup needed, so this works even while Group 5's Directory Service is down. */
    private EligibilityResult checkRoles(String userId, EligibilityRule rule, String bearerToken) {
        UserResponse response = restClient.get()
                .uri("/api/v1/validation/users/{userId}", userId)
                .headers(headers -> bearer(headers, bearerToken))
                .retrieve()
                .body(UserResponse.class);

        if (response == null || !Boolean.TRUE.equals(response.success())
                || response.data() == null || response.data().isValid() == null) {
            log.warn("Group 5 returned an unusable user validation reply for user {}", userId);
            return EligibilityResult.unavailable();
        }
        if (!response.data().isValid()) {
            return EligibilityResult.invalidUser();
        }
        return holdsAllowedRole(rule, response.data().roles())
                ? EligibilityResult.eligible() : EligibilityResult.ineligible(NO_ALLOWED_ROLE);
    }

    private EligibilityResult checkAffiliation(String userId, EligibilityRule rule, String bearerToken) {
        EligibilityResponse response = restClient.get()
                .uri(builder -> {
                    builder.path("/api/v1/validation/users/{userId}/eligibility")
                            .queryParam("relationship", "AFFILIATION");
                    if (rule.departmentId() != null) {
                        builder.queryParam("department_id", rule.departmentId());
                    }
                    if (rule.facultyId() != null) {
                        builder.queryParam("faculty_id", rule.facultyId());
                    }
                    return builder.build(userId);
                })
                .headers(headers -> bearer(headers, bearerToken))
                .retrieve()
                .body(EligibilityResponse.class);

        if (response == null || !Boolean.TRUE.equals(response.success())
                || response.data() == null || response.data().eligible() == null) {
            log.warn("Group 5 returned an unusable eligibility reply for user {}", userId);
            return EligibilityResult.unavailable();
        }
        if (!response.data().eligible()) {
            return EligibilityResult.ineligible(clean(response.data().message()));
        }
        return holdsAllowedRole(rule, response.data().roles())
                ? EligibilityResult.eligible() : EligibilityResult.ineligible(NO_ALLOWED_ROLE);
    }

    private static boolean holdsAllowedRole(EligibilityRule rule, List<String> userRoles) {
        if (rule.roles().isEmpty()) {
            return true;
        }
        return userRoles != null && userRoles.stream()
                .map(role -> role.trim().toUpperCase())
                .anyMatch(rule.roles()::contains);
    }

    private static void bearer(HttpHeaders headers, String bearerToken) {
        if (bearerToken != null) {
            headers.setBearerAuth(bearerToken);
        }
    }

    private static String clean(String message) {
        if (message == null || message.isBlank()) {
            return null;
        }
        return message.length() > MAX_MESSAGE_LENGTH ? message.substring(0, MAX_MESSAGE_LENGTH) : message;
    }

    /** Only the fields event-service needs from Group 5's replies; anything else is ignored. */
    record UserResponse(Boolean success, Data data) {
        record Data(@JsonProperty("is_valid") Boolean isValid, List<String> roles) {
        }
    }

    record EligibilityResponse(Boolean success, Data data) {
        record Data(Boolean eligible, List<String> roles, String message) {
        }
    }
}
