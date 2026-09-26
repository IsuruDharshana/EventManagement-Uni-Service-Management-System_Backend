package com.group8.eventservice.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

public record EventsOverview(
        @Schema(description = "draft + published + cancelled + completed", example = "7") long totalEvents,
        @Schema(example = "1") long draft,
        @Schema(example = "3") long published,
        @Schema(example = "1") long cancelled,
        @Schema(example = "2") long completed,
        @Schema(example = "40") long confirmedRegistrations,
        @Schema(example = "5") long cancelledRegistrations) {
}
