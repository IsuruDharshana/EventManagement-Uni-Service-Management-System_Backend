package com.group8.eventservice.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.group8.eventservice.entity.Registration;
import com.group8.eventservice.entity.RegistrationStatus;

public interface RegistrationRepository extends JpaRepository<Registration, UUID> {

    long countByEvent_IdAndStatus(UUID eventId, RegistrationStatus status);

    long countByStatus(RegistrationStatus status);

    List<Registration> findByUserId(String userId);

    @Query("select r.userId from Registration r where r.event.id = :eventId and r.status = :status")
    List<String> findUserIdsByEventIdAndStatus(@Param("eventId") UUID eventId, @Param("status") RegistrationStatus status);
}
