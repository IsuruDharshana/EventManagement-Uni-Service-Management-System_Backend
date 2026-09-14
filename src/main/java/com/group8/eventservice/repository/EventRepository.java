package com.group8.eventservice.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.group8.eventservice.entity.Event;

import jakarta.persistence.LockModeType;

public interface EventRepository extends JpaRepository<Event, UUID> {

    /**
     * Locks the event row for the duration of the transaction, serializing concurrent
     * registration attempts on the same event so the capacity check + insert in
     * RegistrationService can't race (two requests both reading "capacity not yet reached").
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from Event e where e.id = :id")
    Optional<Event> findByIdForUpdate(@Param("id") UUID id);
}
