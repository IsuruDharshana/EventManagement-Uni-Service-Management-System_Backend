package com.group8.eventservice.dto.request;

import java.time.LocalDateTime;

import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/** Partial update — every field is optional; a null field means "leave unchanged". */
@Getter
@Setter
public class UpdateEventRequest {

    @Size(max = 200)
    private String title;

    private String description;

    @Size(max = 200)
    private String venue;

    private Boolean online;

    private LocalDateTime scheduleStart;

    private LocalDateTime scheduleEnd;

    @Positive
    private Integer capacity;

    private String eligibilityRule;

    private LocalDateTime registrationOpenAt;

    private LocalDateTime registrationCloseAt;
}
