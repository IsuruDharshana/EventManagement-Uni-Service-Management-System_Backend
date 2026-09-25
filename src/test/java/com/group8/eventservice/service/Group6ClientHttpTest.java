package com.group8.eventservice.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import com.sun.net.httpserver.HttpServer;

/** Exercises Group6Client against Group 6's documented replies (Venue Validation Integration Spec, 25 Sep 2026). */
class Group6ClientHttpTest {

    private static final String VALID = """
            {"success": true, "message": "Group 8 resource validation completed",
             "data": {"resourceId": 1, "resourceCode": "LAB-101", "facilityId": 1, "exists": true, "active": true,
                      "available": true, "capacity": 30, "approvalRequired": false,
                      "operatingHoursStart": "08:00:00", "operatingHoursEnd": "20:00:00", "validForReservation": true,
                      "message": "Resource is valid and available for reservation"},
             "timestamp": "2026-09-25T19:22:00"}""";

    private static final String NOT_FOUND = """
            {"success": true, "message": "Group 8 resource validation completed",
             "data": {"resourceId": 999, "resourceCode": null, "facilityId": null, "exists": false, "active": false,
                      "available": false, "capacity": null, "approvalRequired": false,
                      "operatingHoursStart": null, "operatingHoursEnd": null, "validForReservation": false,
                      "message": "Resource with ID 999 does not exist"},
             "timestamp": "2026-09-25T19:22:00"}""";

    private static final String UNAVAILABLE = """
            {"success": true, "message": "Group 8 resource validation completed",
             "data": {"resourceId": 1, "resourceCode": "LAB-101", "facilityId": 1, "exists": true, "active": true,
                      "available": false, "capacity": 30, "approvalRequired": false,
                      "operatingHoursStart": "08:00:00", "operatingHoursEnd": "20:00:00", "validForReservation": false,
                      "message": "Resource is currently marked unavailable"},
             "timestamp": "2026-09-25T19:22:00"}""";

    private HttpServer server;
    private final AtomicReference<String> requestedPath = new AtomicReference<>();

    @AfterEach
    void stop() {
        if (server != null) {
            server.stop(0);
        }
    }

    private VenueResult callWith(int status, String body, long delayMs, String venue) throws IOException {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/", exchange -> {
            requestedPath.set(exchange.getRequestURI().getRawPath());
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
        Group6Client client = new Group6Client(RestClient.builder().requestFactory(factory),
                "http://localhost:" + server.getAddress().getPort(), false);
        return client.validateVenue(venue);
    }

    private VenueResult callWith(int status, String body, long delayMs) throws IOException {
        return callWith(status, body, delayMs, "LAB-101");
    }

    @Test
    void validVenueIsAccepted() throws IOException {
        VenueResult result = callWith(200, VALID, 0);

        assertThat(result.isValid()).isTrue();
        assertThat(requestedPath.get()).isEqualTo("/api/resources/code/LAB-101/validate");
    }

    @Test
    void unknownVenueIsReportedWithGroup6Message() throws IOException {
        VenueResult result = callWith(200, NOT_FOUND, 0);

        assertThat(result.status()).isEqualTo(VenueResult.Status.NOT_FOUND);
        assertThat(result.message()).isEqualTo("Resource with ID 999 does not exist");
    }

    @Test
    void unavailableVenueIsReportedWithGroup6Message() throws IOException {
        VenueResult result = callWith(200, UNAVAILABLE, 0);

        assertThat(result.status()).isEqualTo(VenueResult.Status.NOT_AVAILABLE);
        assertThat(result.message()).isEqualTo("Resource is currently marked unavailable");
    }

    @Test
    void venueCodeWithSpacesIsEncodedInThePath() throws IOException {
        callWith(200, NOT_FOUND, 0, "Lab B 204");

        assertThat(requestedPath.get()).isEqualTo("/api/resources/code/Lab%20B%20204/validate");
    }

    @Test
    void serverErrorMeansServiceUnavailable() throws IOException {
        assertThat(callWith(500, "{}", 0).status()).isEqualTo(VenueResult.Status.SERVICE_UNAVAILABLE);
    }

    @Test
    void notFoundStatusFromTheServerMeansServiceUnavailable() throws IOException {
        assertThat(callWith(404, "{}", 0).status()).isEqualTo(VenueResult.Status.SERVICE_UNAVAILABLE);
    }

    @Test
    void slowResponseTimesOutAsServiceUnavailable() throws IOException {
        assertThat(callWith(200, VALID, 1500).status()).isEqualTo(VenueResult.Status.SERVICE_UNAVAILABLE);
    }

    @Test
    void emptyBodyMeansServiceUnavailable() throws IOException {
        assertThat(callWith(200, "", 0).status()).isEqualTo(VenueResult.Status.SERVICE_UNAVAILABLE);
    }

    @Test
    void garbledBodyMeansServiceUnavailable() throws IOException {
        assertThat(callWith(200, "<html>oops</html>", 0).status()).isEqualTo(VenueResult.Status.SERVICE_UNAVAILABLE);
    }

    @Test
    void unsuccessfulReplyMeansServiceUnavailable() throws IOException {
        assertThat(callWith(200, "{\"success\": false, \"data\": null}", 0).status())
                .isEqualTo(VenueResult.Status.SERVICE_UNAVAILABLE);
    }
}
