package com.group8.eventservice.dto.request;

import java.time.LocalDateTime;

import com.group8.eventservice.entity.EventStatus;

/**
 * Optional filters for GET /api/events. Every field may be null / false, which means "no filter".
 * page and size are both null when the caller wants the whole (filtered) list, as before.
 */
public record EventFilter(
        EventStatus status,
        LocalDateTime from,
        LocalDateTime to,
        boolean upcoming,
        boolean mine,
        Integer page,
        Integer size) {

    public static EventFilter none() {
        return new EventFilter(null, null, null, false, false, null, null);
    }
}
