package com.group8.eventservice.repository;

import java.time.LocalDateTime;

import org.springframework.data.jpa.domain.Specification;

import com.group8.eventservice.entity.Event;
import com.group8.eventservice.entity.EventStatus;

/** Query building blocks for the event list, combined in EventService. */
public final class EventSpecifications {

    private EventSpecifications() {
    }

    /** What a user who cannot see all events may see: published events plus the ones they organize. */
    public static Specification<Event> visibleTo(String userId) {
        return (root, query, cb) -> cb.or(
                cb.equal(root.get("status"), EventStatus.PUBLISHED),
                cb.equal(root.get("organizerId"), userId));
    }

    public static Specification<Event> hasStatus(EventStatus status) {
        return (root, query, cb) -> cb.equal(root.get("status"), status);
    }

    public static Specification<Event> organizedBy(String userId) {
        return (root, query, cb) -> cb.equal(root.get("organizerId"), userId);
    }

    public static Specification<Event> startsAtOrAfter(LocalDateTime time) {
        return (root, query, cb) -> cb.greaterThanOrEqualTo(root.get("scheduleStart"), time);
    }

    public static Specification<Event> startsAtOrBefore(LocalDateTime time) {
        return (root, query, cb) -> cb.lessThanOrEqualTo(root.get("scheduleStart"), time);
    }
}
