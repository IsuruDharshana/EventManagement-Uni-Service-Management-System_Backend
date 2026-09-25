package com.group8.eventservice.dto.request;

import java.time.LocalDateTime;

import io.swagger.v3.oas.annotations.media.Schema;
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

    @Schema(example = "Innovation Week Workshop")
    @NotBlank
    @Size(max = 200)
    private String title;

    @Schema(example = "Hands-on workshop for Computing students")
    private String description;

    @Schema(example = "Lab B-204", description = "Leave empty for online events")
    @Size(max = 200)
    private String venue;

    @Schema(example = "false")
    private boolean online;

    @Schema(example = "2026-10-05T10:00:00")
    @NotNull
    private LocalDateTime scheduleStart;

    @Schema(example = "2026-10-05T13:00:00", description = "Must be after scheduleStart")
    @NotNull
    private LocalDateTime scheduleEnd;

    @Schema(example = "30", description = "Must be greater than 0")
    @NotNull
    @Positive
    private Integer capacity;

    @Schema(example = "{\"department\": \"Computing\"}", description = "JSON rule saying who may register; {\"all\": true} means everyone")
    @NotBlank
    private String eligibilityRule;

    @Schema(example = "2026-09-25T08:00:00")
    @NotNull
    private LocalDateTime registrationOpenAt;

    @Schema(example = "2026-10-04T18:00:00", description = "Must be on or before scheduleStart")
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
