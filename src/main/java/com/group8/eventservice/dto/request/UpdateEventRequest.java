package com.group8.eventservice.dto.request;

import java.time.LocalDateTime;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/** Partial update — every field is optional; a null field means "leave unchanged". */
@Getter
@Setter
@Schema(description = "Partial update: send only the fields to change; missing or null fields stay as they are. Same rules as create (dates must fit together, eligibilityRule in the supported format).")
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
