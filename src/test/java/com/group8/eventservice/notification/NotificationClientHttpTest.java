package com.group8.eventservice.notification;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import com.sun.net.httpserver.HttpServer;

/** NotificationClient against a fake communication-feedback-service. */
class NotificationClientHttpTest {

    private static final NotificationRequest CONFIRMED = new NotificationRequest("usr-student-001",
            "REGISTRATION_CONFIRMED", "You are registered for Innovation Week.", "REGISTRATION",
            "b0000000-0000-0000-0000-000000000001", "event-service",
            "REGISTRATION_CONFIRMED:b0000000-0000-0000-0000-000000000001");

    private HttpServer server;
    private final List<String> paths = new CopyOnWriteArrayList<>();
    private final List<String> keys = new CopyOnWriteArrayList<>();
    private final List<String> bodies = new CopyOnWriteArrayList<>();

    @AfterEach
    void stop() {
        if (server != null) {
            server.stop(0);
        }
    }

    private NotificationClient clientReplying(int status, long delayMs) throws IOException {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/", exchange -> {
            paths.add(exchange.getRequestMethod() + " " + exchange.getRequestURI());
            keys.add(String.valueOf(exchange.getRequestHeaders().getFirst("X-Service-Key")));
            bodies.add(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            try {
                Thread.sleep(delayMs);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
            byte[] reply = "{\"id\": \"n-1\"}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, reply.length);
            exchange.getResponseBody().write(reply);
            exchange.close();
        });
        server.start();

        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(500);
        factory.setReadTimeout(300);
        return new NotificationClient(RestClient.builder().requestFactory(factory),
                "http://localhost:" + server.getAddress().getPort(), false, "test-service-key");
    }

    @Test
    void postsTheNotificationWithTheServiceKey() throws IOException {
        assertThat(clientReplying(201, 0).send(CONFIRMED)).isTrue();

        assertThat(paths).containsExactly("POST /api/notifications/trigger");
        assertThat(keys).containsExactly("test-service-key");
        assertThat(bodies.get(0))
                .contains("\"recipientId\":\"usr-student-001\"")
                .contains("\"type\":\"REGISTRATION_CONFIRMED\"")
                .contains("\"sourceService\":\"event-service\"")
                .contains("\"idempotencyKey\":\"REGISTRATION_CONFIRMED:b0000000-0000-0000-0000-000000000001\"");
    }

    @Test
    void alreadySentKeyCountsAsDelivered() throws IOException {
        assertThat(clientReplying(200, 0).send(CONFIRMED)).isTrue();
    }

    @Test
    void rejectedOrFailedCallsAreReportedNotThrown() throws IOException {
        assertThat(clientReplying(401, 0).send(CONFIRMED)).isFalse();
        stop();
        assertThat(clientReplying(500, 0).send(CONFIRMED)).isFalse();
        stop();
        assertThat(clientReplying(201, 1500).send(CONFIRMED)).isFalse();
    }

    @Test
    void unreachableServiceIsReportedNotThrown() {
        NotificationClient client = new NotificationClient(RestClient.builder(), "http://localhost:1", false, "k");
        assertThat(client.send(CONFIRMED)).isFalse();
    }

    @Test
    void mockModeNeverCallsOut() {
        NotificationClient client = new NotificationClient(RestClient.builder(), "http://localhost:1", true, "");
        assertThat(client.send(CONFIRMED)).isTrue();
    }
}
