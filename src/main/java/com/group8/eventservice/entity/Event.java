package com.group8.eventservice.entity;

import java.time.LocalDateTime;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "events")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Event {

    @Id
    @UuidGenerator
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(updatable = false, nullable = false, length = 36)
    private UUID id;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "organizer_id", nullable = false, length = 36)
    private UUID organizerId;

    @Column(length = 200)
    private String venue;

    @Column(name = "is_online", nullable = false)
    private boolean online;

    @Column(name = "schedule_start", nullable = false)
    private LocalDateTime scheduleStart;

    @Column(name = "schedule_end", nullable = false)
    private LocalDateTime scheduleEnd;

    @Column(nullable = false)
    private Integer capacity;

    /** Free-form eligibility rule, e.g. {"faculty": "Engineering", "year": [2,3]}. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "eligibility_rule", nullable = false, columnDefinition = "json")
    private String eligibilityRule;

    @Column(name = "registration_open_at", nullable = false)
    private LocalDateTime registrationOpenAt;

    @Column(name = "registration_close_at", nullable = false)
    private LocalDateTime registrationCloseAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private EventStatus status = EventStatus.DRAFT;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
