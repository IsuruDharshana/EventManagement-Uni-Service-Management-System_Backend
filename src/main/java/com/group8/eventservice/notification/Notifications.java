package com.group8.eventservice.notification;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

import com.group8.eventservice.entity.Event;
import com.group8.eventservice.entity.Registration;

/**
 * Builds the notifications event-service sends. Messages are shown to users, so they hold the
 * event title only, never ids. Each idempotency key is unique per real-world change, so a
 * repeated send never creates a second notification.
 */
public final class Notifications {

    public static final String SOURCE = "event-service";

    private static final DateTimeFormatter VERSION = DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS");

    private Notifications() {
    }

    public static NotificationsRequested registrationConfirmed(Registration registration) {
        return registration(registration, "REGISTRATION_CONFIRMED",
                "You are registered for " + registration.getEvent().getTitle() + ".");
    }

    public static NotificationsRequested registrationCancelled(Registration registration) {
        return registration(registration, "REGISTRATION_CANCELLED",
                "Your registration for " + registration.getEvent().getTitle() + " has been cancelled.");
    }

    public static NotificationsRequested eventCancelled(Event event, List<String> recipientIds) {
        return toEach(recipientIds, event, "EVENT_CANCELLED",
                event.getTitle() + " has been cancelled.",
                "EVENT_CANCELLED:" + event.getId());
    }

    /** The key includes the event's updatedAt so every separate change is notified once. */
    public static NotificationsRequested eventUpdated(Event event, List<String> recipientIds) {
        return toEach(recipientIds, event, "EVENT_UPDATED",
                event.getTitle() + " has changed. Please check the new date, time and venue.",
                "EVENT_UPDATED:" + event.getId() + ":"
                        + (event.getUpdatedAt() != null ? event.getUpdatedAt() : LocalDateTime.now()).format(VERSION));
    }

    private static NotificationsRequested registration(Registration registration, String type, String message) {
        String id = registration.getId().toString();
        return new NotificationsRequested(List.of(new NotificationRequest(registration.getUserId(), type, message,
                "REGISTRATION", id, SOURCE, type + ":" + id)));
    }

    private static NotificationsRequested toEach(List<String> recipientIds, Event event, String type,
                                                 String message, String keyPrefix) {
        String eventId = event.getId().toString();
        return new NotificationsRequested(recipientIds.stream()
                .map(userId -> new NotificationRequest(userId, type, message, "EVENT", eventId, SOURCE,
                        keyPrefix + ":" + userId))
                .toList());
    }
}
