package com.group8.eventservice.notification;

/**
 * Body of communication-feedback-service's POST /api/notifications/trigger
 * (see docs/notification-api-contract.yaml).
 */
public record NotificationRequest(
        String recipientId,
        String type,
        String message,
        String relatedType,
        String relatedId,
        String sourceService,
        String idempotencyKey) {
}
