package com.group8.eventservice.notification;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import lombok.RequiredArgsConstructor;

/**
 * Sends requested notifications after the database change commits, on a background thread, so
 * the user's request never waits for (or fails because of) the notification service. If the
 * transaction rolls back, nothing is sent.
 */
@Component
@RequiredArgsConstructor
public class NotificationListener {

    private final NotificationClient notificationClient;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onNotificationsRequested(NotificationsRequested requested) {
        requested.notifications().forEach(notificationClient::send);
    }
}
