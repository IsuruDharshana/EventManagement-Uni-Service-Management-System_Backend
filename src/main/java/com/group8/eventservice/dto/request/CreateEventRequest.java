package com.group8.eventservice.dto.request;

import java.time.LocalDateTime;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CreateEventRequest {

    @NotBlank
    @Size(max = 200)
    private String title;

    private String description;

    @Size(max = 200)
    private String venue;

    private boolean online;

    @NotNull
    private LocalDateTime scheduleStart;

    @NotNull
    private LocalDateTime scheduleEnd;

    @NotNull
    @Positive
    private Integer capacity;

    @NotBlank
    private String eligibilityRule;

    @NotNull
    private LocalDateTime registrationOpenAt;

    @NotNull
    private LocalDateTime registrationCloseAt;

    @AssertTrue(message = "scheduleEnd must be after scheduleStart")
    private boolean isScheduleValid() {
        return scheduleStart == null || scheduleEnd == null || scheduleEnd.isAfter(scheduleStart);
    }

    @AssertTrue(message = "registrationCloseAt must be before or equal to scheduleStart")
    private boolean isRegistrationWindowValid() {
        return registrationCloseAt == null || scheduleStart == null || !registrationCloseAt.isAfter(scheduleStart);
    }
}
