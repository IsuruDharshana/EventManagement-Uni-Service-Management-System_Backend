package com.group8.eventservice.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.group8.eventservice.entity.Registration;

public interface RegistrationRepository extends JpaRepository<Registration, UUID> {
}
