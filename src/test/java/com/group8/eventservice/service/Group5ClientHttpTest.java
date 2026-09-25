package com.group8.eventservice.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import com.sun.net.httpserver.HttpServer;

class Group5ClientHttpTest {

    private HttpServer server;

    @AfterEach
    void stop() {
        if (server != null) {
            server.stop(0);
        }
    }

    private EligibilityResult callWith(int status, String body, long delayMs) throws IOException {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/", exchange -> {
            try {
                Thread.sleep(delayMs);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, bytes.length == 0 ? -1 : bytes.length);
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
        return client.checkEligibility(UUID.randomUUID(), UUID.randomUUID());
    }

    @Test
    void trueBodyMeansEligible() throws IOException {
        assertThat(callWith(200, "true", 0).status()).isEqualTo(EligibilityResult.Status.ELIGIBLE);
    }

    @Test
    void falseBodyMeansIneligible() throws IOException {
        assertThat(callWith(200, "false", 0).status()).isEqualTo(EligibilityResult.Status.INELIGIBLE);
    }

    @Test
    void unknownUserMeansInvalidUser() throws IOException {
        assertThat(callWith(404, "{}", 0).status()).isEqualTo(EligibilityResult.Status.INVALID_USER);
    }

    @Test
    void serverErrorMeansUnavailable() throws IOException {
        assertThat(callWith(500, "{}", 0).status()).isEqualTo(EligibilityResult.Status.UNAVAILABLE);
    }

    @Test
    void slowResponseTimesOutAsUnavailable() throws IOException {
        assertThat(callWith(200, "true", 1500).status()).isEqualTo(EligibilityResult.Status.UNAVAILABLE);
    }

    @Test
    void emptyBodyMeansUnavailable() throws IOException {
        assertThat(callWith(200, "", 0).status()).isEqualTo(EligibilityResult.Status.UNAVAILABLE);
    }

    @Test
    void garbledBodyMeansUnavailable() throws IOException {
        assertThat(callWith(200, "<html>oops</html>", 0).status()).isEqualTo(EligibilityResult.Status.UNAVAILABLE);
    }
}
