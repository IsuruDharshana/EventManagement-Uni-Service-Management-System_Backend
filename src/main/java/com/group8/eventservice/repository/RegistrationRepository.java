package com.group8.eventservice.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.group8.eventservice.entity.Registration;
import com.group8.eventservice.entity.RegistrationStatus;

public interface RegistrationRepository extends JpaRepository<Registration, UUID> {

    long countByEvent_IdAndStatus(UUID eventId, RegistrationStatus status);

    long countByStatus(RegistrationStatus status);

    List<Registration> findByUserId(UUID userId);
}
