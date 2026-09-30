package com.group8.eventservice.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import com.sun.net.httpserver.HttpServer;

/** Group5Client against a fake Identity Service replying with Group 5's documented v1 payloads. */
class Group5ClientHttpTest {

    private static final String TOKEN = "caller-token";
    private static final EligibilityRule STUDENTS = EligibilityRule.parse("{\"roles\": [\"STUDENT\"]}");
    private static final EligibilityRule STAFF = EligibilityRule.parse("{\"roles\": [\"ACADEMIC_STAFF\", \"ADMIN\"]}");
    private static final EligibilityRule CS_STUDENTS =
            EligibilityRule.parse("{\"roles\": [\"STUDENT\"], \"departmentId\": \"CS\"}");
    private static final EligibilityRule SCIENCE_FACULTY = EligibilityRule.parse("{\"facultyId\": \"FSC\"}");

    private static final String ACTIVE_STUDENT = """
            {"success": true, "data": {"user_id": "usr-student-001", "university_id": "STU001", "name": "Demo Student",
             "account_type": "STUDENT", "status": "ACTIVE", "is_valid": true, "roles": ["STUDENT"],
             "is_authorized": true, "required_role_checked": null}}
            """;
    private static final String INACTIVE_STUDENT = """
            {"success": true, "data": {"user_id": "usr-student-002", "status": "INACTIVE", "is_valid": false,
             "roles": ["STUDENT"], "is_authorized": true}}
            """;
    private static final String CS_ELIGIBLE = """
            {"success": true, "data": {"user_id": "usr-student-001", "account_status": "ACTIVE", "roles": ["STUDENT"],
             "eligible": true, "reasons": [], "message": "User is eligible.",
             "checks": {"account_active": true, "relationship": "AFFILIATION", "relationship_satisfied": true},
             "matched_responsibilities": [], "affiliation": {"department_id": "dept-cs-cea025"}}}
            """;
    private static final String NOT_AFFILIATED = """
            {"success": true, "data": {"user_id": "usr-student-003", "roles": ["STUDENT"], "eligible": false,
             "reasons": ["AFFILIATION_MISMATCH"],
             "message": "User is not affiliated with the requested department/faculty."}}
            """;
    private static final String DIRECTORY_DOWN = """
            {"success": false, "error": {"code": "DEPENDENCY_UNAVAILABLE",
             "message": "The Directory Service is currently unavailable."}, "timestamp": "2026-09-26T10:15:30.123Z"}
            """;

    private record Reply(int status, String body, long delayMs) {
    }

    private HttpServer server;
    private final Deque<Reply> replies = new ArrayDeque<>();
    private final List<String> requests = new CopyOnWriteArrayList<>();
    private final List<String> authHeaders = new CopyOnWriteArrayList<>();

    @AfterEach
    void stop() {
        if (server != null) {
            server.stop(0);
        }
    }

    private void reply(int status, String body) {
        replies.add(new Reply(status, body, 0));
    }

    private EligibilityResult check(EligibilityRule rule) throws IOException {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/", exchange -> {
            requests.add(exchange.getRequestURI().toString());
            authHeaders.add(String.valueOf(exchange.getRequestHeaders().getFirst("Authorization")));
            Reply reply = replies.isEmpty() ? new Reply(500, "{}", 0) : replies.poll();
            try {
                Thread.sleep(reply.delayMs());
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
            byte[] bytes = reply.body().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(reply.status(), bytes.length == 0 ? -1 : bytes.length);
            if (bytes.length > 0) {
                exchange.getResponseBody().write(bytes);
            }
            exchange.close();
        });
        server.start();

        Group5Client client = new Group5Client(RestClient.builder(), "http://localhost:" + server.getAddress().getPort(),
                false, Duration.ofMillis(500), Duration.ofMillis(300));
        return client.checkEligibility("usr-student-001", rule, TOKEN);
    }

    @Test
    void roleOnlyRuleUsesTheUserValidationEndpointOnce() throws IOException {
        reply(200, ACTIVE_STUDENT);

        assertThat(check(STUDENTS).status()).isEqualTo(EligibilityResult.Status.ELIGIBLE);
        assertThat(requests).containsExactly("/api/v1/validation/users/usr-student-001");
        assertThat(authHeaders).containsExactly("Bearer " + TOKEN);
    }

    @Test
    void severalAllowedRolesStillNeedOnlyOneCall() throws IOException {
        reply(200, ACTIVE_STUDENT);

        EligibilityResult result = check(STAFF);

        assertThat(result.status()).isEqualTo(EligibilityResult.Status.INELIGIBLE);
        assertThat(result.message()).isEqualTo(Group5Client.NO_ALLOWED_ROLE);
        assertThat(requests).hasSize(1);
    }

    @Test
    void inactiveAccountIsInvalidUser() throws IOException {
        reply(200, INACTIVE_STUDENT);
        assertThat(check(STUDENTS).status()).isEqualTo(EligibilityResult.Status.INVALID_USER);
    }

    @Test
    void departmentRuleUsesAffiliationWithTheCodeAndChecksRoles() throws IOException {
        reply(200, CS_ELIGIBLE);

        assertThat(check(CS_STUDENTS).status()).isEqualTo(EligibilityResult.Status.ELIGIBLE);
        assertThat(requests).containsExactly(
                "/api/v1/validation/users/usr-student-001/eligibility?relationship=AFFILIATION&department_id=CS");
    }

    @Test
    void affiliatedButWithoutAnAllowedRoleIsIneligible() throws IOException {
        reply(200, CS_ELIGIBLE);

        EligibilityResult result = check(EligibilityRule.parse("{\"roles\": [\"ACADEMIC_STAFF\"], \"departmentId\": \"CS\"}"));

        assertThat(result.status()).isEqualTo(EligibilityResult.Status.INELIGIBLE);
        assertThat(result.message()).isEqualTo(Group5Client.NO_ALLOWED_ROLE);
    }

    @Test
    void facultyOnlyRuleSendsNoRoleCheck() throws IOException {
        reply(200, CS_ELIGIBLE);

        assertThat(check(SCIENCE_FACULTY).status()).isEqualTo(EligibilityResult.Status.ELIGIBLE);
        assertThat(requests).containsExactly(
                "/api/v1/validation/users/usr-student-001/eligibility?relationship=AFFILIATION&faculty_id=FSC");
    }

    @Test
    void notAffiliatedGetsGroup5sReason() throws IOException {
        reply(200, NOT_AFFILIATED);

        EligibilityResult result = check(CS_STUDENTS);

        assertThat(result.status()).isEqualTo(EligibilityResult.Status.INELIGIBLE);
        assertThat(result.message()).isEqualTo("User is not affiliated with the requested department/faculty.");
    }

    @Test
    void directoryServiceDownIsUnavailableNeverEligible() throws IOException {
        reply(503, DIRECTORY_DOWN);
        assertThat(check(CS_STUDENTS).status()).isEqualTo(EligibilityResult.Status.UNAVAILABLE);
    }

    @Test
    void unknownUserIsInvalidUser() throws IOException {
        reply(404, "{\"success\": false, \"error\": {\"code\": \"USER_NOT_FOUND\", \"message\": \"not found\"}}");
        assertThat(check(STUDENTS).status()).isEqualTo(EligibilityResult.Status.INVALID_USER);
    }

    @Test
    void rejectedTokenIsInvalidUser() throws IOException {
        reply(401, "{\"success\": false, \"error\": {\"code\": \"INVALID_TOKEN\", \"message\": \"expired\"}}");
        assertThat(check(STUDENTS).status()).isEqualTo(EligibilityResult.Status.INVALID_USER);
    }

    @Test
    void validationErrorIsUnavailableNotEligible() throws IOException {
        reply(422, "{\"success\": false, \"error\": {\"code\": \"VALIDATION_ERROR\", \"message\": \"bad\"}}");
        assertThat(check(CS_STUDENTS).status()).isEqualTo(EligibilityResult.Status.UNAVAILABLE);
    }

    @Test
    void slowResponseTimesOutAsUnavailableWithoutRetrying() throws IOException {
        replies.add(new Reply(200, ACTIVE_STUDENT, 1500));

        assertThat(check(STUDENTS).status()).isEqualTo(EligibilityResult.Status.UNAVAILABLE);
        assertThat(requests).hasSize(1);
    }

    @Test
    void emptyOrGarbledRepliesAreUnavailable() throws IOException {
        reply(200, "");
        assertThat(check(STUDENTS).status()).isEqualTo(EligibilityResult.Status.UNAVAILABLE);
        stop();
        reply(200, "<html>oops</html>");
        assertThat(check(CS_STUDENTS).status()).isEqualTo(EligibilityResult.Status.UNAVAILABLE);
        stop();
        reply(200, "{\"success\": true, \"data\": {\"roles\": [\"STUDENT\"]}}");
        assertThat(check(STUDENTS).status()).isEqualTo(EligibilityResult.Status.UNAVAILABLE);
    }
}
