package com.group8.eventservice.repository;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.group8.eventservice.entity.Event;
import com.group8.eventservice.entity.EventStatus;

import jakarta.persistence.LockModeType;

public interface EventRepository extends JpaRepository<Event, UUID> {

    long countByStatus(EventStatus status);

    /**
     * Locks the event row for the duration of the transaction, serializing concurrent
     * registration attempts on the same event so the capacity check + insert in
     * RegistrationService can't race (two requests both reading "capacity not yet reached").
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from Event e where e.id = :id")
    Optional<Event> findByIdForUpdate(@Param("id") UUID id);

    /** Moves every PUBLISHED event whose end time has passed to COMPLETED. Returns how many changed. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update Event e set e.status = com.group8.eventservice.entity.EventStatus.COMPLETED, e.updatedAt = :now "
            + "where e.status = com.group8.eventservice.entity.EventStatus.PUBLISHED and e.scheduleEnd <= :now")
    int completeFinishedEvents(@Param("now") LocalDateTime now);
}
