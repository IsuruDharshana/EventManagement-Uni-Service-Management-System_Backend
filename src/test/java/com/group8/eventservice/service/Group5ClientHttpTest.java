package com.group8.eventservice.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import com.sun.net.httpserver.HttpServer;

/** Group5Client against a fake Identity Service speaking Group 5's documented v1 contract. */
class Group5ClientHttpTest {

    private static final String TOKEN = "caller-token";
    private static final EligibilityRule CS_STUDENTS =
            EligibilityRule.parse("{\"roles\": [\"STUDENT\"], \"departmentId\": \"dep-cs\"}");

    private static final String ELIGIBLE = """
            {"success": true, "data": {"user_id": "usr-student-001", "university_id": "STU001",
             "account_status": "ACTIVE", "roles": ["STUDENT"], "eligible": true, "reasons": [],
             "message": "User is eligible.", "checks": {"account_active": true, "role_held": true},
             "matched_responsibilities": [], "affiliation": {"department_id": "dep-cs"}}}
            """;
    private static final String NOT_AFFILIATED = """
            {"success": true, "data": {"user_id": "usr-student-002", "eligible": false,
             "reasons": ["AFFILIATION_MISMATCH"],
             "message": "User is not affiliated with the requested department/faculty."}}
            """;
    private static final String ROLE_NOT_HELD = """
            {"success": true, "data": {"eligible": false, "reasons": ["ROLE_NOT_HELD"],
             "message": "User does not hold the required role."}}
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

        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(500);
        factory.setReadTimeout(300);
        Group5Client client = new Group5Client(RestClient.builder().requestFactory(factory),
                "http://localhost:" + server.getAddress().getPort(), false);
        return client.checkEligibility("usr-student-001", rule, TOKEN);
    }

    @Test
    void eligibleUserIsEligibleAndTheQueryMatchesTheRule() throws IOException {
        reply(200, ELIGIBLE);

        assertThat(check(CS_STUDENTS).status()).isEqualTo(EligibilityResult.Status.ELIGIBLE);
        assertThat(requests).containsExactly("/api/v1/validation/users/usr-student-001/eligibility"
                + "?required_role=STUDENT&relationship=AFFILIATION&department_id=dep-cs");
        assertThat(authHeaders).containsExactly("Bearer " + TOKEN);
    }

    @Test
    void roleOnlyRuleSendsNoRelationship() throws IOException {
        reply(200, ELIGIBLE);

        check(EligibilityRule.parse("{\"roles\": [\"ACADEMIC_STAFF\"]}"));

        assertThat(requests).containsExactly("/api/v1/validation/users/usr-student-001/eligibility?required_role=ACADEMIC_STAFF");
    }

    @Test
    void facultyOnlyRuleSendsNoRole() throws IOException {
        reply(200, ELIGIBLE);

        check(EligibilityRule.parse("{\"facultyId\": \"fac-sci\"}"));

        assertThat(requests).containsExactly(
                "/api/v1/validation/users/usr-student-001/eligibility?relationship=AFFILIATION&faculty_id=fac-sci");
    }

    @Test
    void ineligibleUserGetsGroup5sReason() throws IOException {
        reply(200, NOT_AFFILIATED);

        EligibilityResult result = check(CS_STUDENTS);

        assertThat(result.status()).isEqualTo(EligibilityResult.Status.INELIGIBLE);
        assertThat(result.message()).isEqualTo("User is not affiliated with the requested department/faculty.");
    }

    @Test
    void severalRolesStopAtTheFirstEligibleAnswer() throws IOException {
        reply(200, ROLE_NOT_HELD);
        reply(200, ELIGIBLE);

        EligibilityResult result = check(EligibilityRule.parse("{\"roles\": [\"STUDENT\", \"ACADEMIC_STAFF\", \"ADMIN\"]}"));

        assertThat(result.status()).isEqualTo(EligibilityResult.Status.ELIGIBLE);
        assertThat(requests).hasSize(2);
        assertThat(requests.get(1)).endsWith("required_role=ACADEMIC_STAFF");
    }

    @Test
    void severalRolesAllIneligibleIsIneligible() throws IOException {
        reply(200, ROLE_NOT_HELD);
        reply(200, ROLE_NOT_HELD);

        EligibilityResult result = check(EligibilityRule.parse("{\"roles\": [\"STUDENT\", \"ACADEMIC_STAFF\"]}"));

        assertThat(result.status()).isEqualTo(EligibilityResult.Status.INELIGIBLE);
        assertThat(result.message()).isEqualTo("User does not hold the required role.");
    }

    @Test
    void outageOnOneRoleIsNeverTreatedAsIneligibleOrEligible() throws IOException {
        reply(503, "{\"success\": false, \"error\": {\"code\": \"DEPENDENCY_UNAVAILABLE\", \"message\": \"down\"}}");
        reply(200, ROLE_NOT_HELD);

        EligibilityResult result = check(EligibilityRule.parse("{\"roles\": [\"STUDENT\", \"ACADEMIC_STAFF\"]}"));

        assertThat(result.status()).isEqualTo(EligibilityResult.Status.UNAVAILABLE);
    }

    @Test
    void unknownUserIsInvalidUser() throws IOException {
        reply(404, "{\"success\": false, \"error\": {\"code\": \"USER_NOT_FOUND\", \"message\": \"not found\"}}");
        assertThat(check(CS_STUDENTS).status()).isEqualTo(EligibilityResult.Status.INVALID_USER);
    }

    @Test
    void inactiveAccountIsInvalidUser() throws IOException {
        reply(403, "{\"success\": false, \"error\": {\"code\": \"ACCOUNT_INACTIVE\", \"message\": \"inactive\"}}");
        assertThat(check(CS_STUDENTS).status()).isEqualTo(EligibilityResult.Status.INVALID_USER);
    }

    @Test
    void rejectedTokenIsInvalidUser() throws IOException {
        reply(401, "{\"success\": false, \"error\": {\"code\": \"INVALID_TOKEN\", \"message\": \"expired\"}}");
        assertThat(check(CS_STUDENTS).status()).isEqualTo(EligibilityResult.Status.INVALID_USER);
    }

    @Test
    void directoryOutageIsUnavailable() throws IOException {
        reply(503, "{\"success\": false, \"error\": {\"code\": \"DEPENDENCY_UNAVAILABLE\", \"message\": \"down\"}}");
        assertThat(check(CS_STUDENTS).status()).isEqualTo(EligibilityResult.Status.UNAVAILABLE);
    }

    @Test
    void validationErrorIsUnavailableNotEligible() throws IOException {
        reply(422, "{\"success\": false, \"error\": {\"code\": \"VALIDATION_ERROR\", \"message\": \"bad\"}}");
        assertThat(check(CS_STUDENTS).status()).isEqualTo(EligibilityResult.Status.UNAVAILABLE);
    }

    @Test
    void slowResponseTimesOutAsUnavailableWithoutRetrying() throws IOException {
        replies.add(new Reply(200, ELIGIBLE, 1500));

        assertThat(check(CS_STUDENTS).status()).isEqualTo(EligibilityResult.Status.UNAVAILABLE);
        assertThat(requests).hasSize(1);
    }

    @Test
    void emptyBodyIsUnavailable() throws IOException {
        reply(200, "");
        assertThat(check(CS_STUDENTS).status()).isEqualTo(EligibilityResult.Status.UNAVAILABLE);
    }

    @Test
    void garbledBodyIsUnavailable() throws IOException {
        reply(200, "<html>oops</html>");
        assertThat(check(CS_STUDENTS).status()).isEqualTo(EligibilityResult.Status.UNAVAILABLE);
    }

    @Test
    void replyWithoutEligibleFlagIsUnavailable() throws IOException {
        reply(200, "{\"success\": true, \"data\": {\"message\": \"?\"}}");
        assertThat(check(CS_STUDENTS).status()).isEqualTo(EligibilityResult.Status.UNAVAILABLE);
    }
}
