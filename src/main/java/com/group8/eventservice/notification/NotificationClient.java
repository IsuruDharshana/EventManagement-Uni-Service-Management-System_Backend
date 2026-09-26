package com.group8.eventservice.notification;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import lombok.extern.slf4j.Slf4j;

/**
 * Sends one notification to communication-feedback-service's POST /api/notifications/trigger,
 * authenticated with the shared X-Service-Key. With integrations.notifications.mock=true
 * (default until that service is available) it only logs. Never throws: a failed notification
 * must not affect the registration or event change that caused it.
 */
@Service
@Slf4j
public class NotificationClient {

    private final RestClient restClient;
    private final boolean mock;
    private final String serviceKey;

    public NotificationClient(RestClient.Builder restClientBuilder,
                              @Value("${integrations.notifications.base-url}") String baseUrl,
                              @Value("${integrations.notifications.mock}") boolean mock,
                              @Value("${integrations.notifications.service-key:}") String serviceKey) {
        this.restClient = restClientBuilder.baseUrl(baseUrl).build();
        this.mock = mock;
        this.serviceKey = serviceKey;
        if (!mock && serviceKey.isBlank()) {
            log.warn("NOTIFICATIONS_SERVICE_KEY is not set; the notification service will reject event-service's calls");
        }
    }

    /** @return true if the notification service accepted it (or mock mode is on) */
    public boolean send(NotificationRequest notification) {
        if (mock) {
            log.info("Notification (mock) {} for {}", notification.type(), notification.recipientId());
            return true;
        }

        try {
            restClient.post()
                    .uri("/api/notifications/trigger")
                    .contentType(MediaType.APPLICATION_JSON)
                    .headers(headers -> {
                        if (!serviceKey.isBlank()) {
                            headers.set("X-Service-Key", serviceKey);
                        }
                    })
                    .body(notification)
                    .retrieve()
                    .toBodilessEntity();
            return true;
        } catch (RestClientException ex) {
            log.warn("Notification {} for {} was not delivered: {}", notification.idempotencyKey(),
                    notification.recipientId(), ex.getMessage());
            return false;
        }
    }
}
