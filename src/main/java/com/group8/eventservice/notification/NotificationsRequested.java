package com.group8.eventservice.notification;

import java.util.List;

/** Published inside a service transaction; the notifications are sent only if that transaction commits. */
public record NotificationsRequested(List<NotificationRequest> notifications) {
}
