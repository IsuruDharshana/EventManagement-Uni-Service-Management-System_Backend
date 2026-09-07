package com.group8.eventservice.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.group8.eventservice.entity.Event;

public interface EventRepository extends JpaRepository<Event, UUID> {
}
