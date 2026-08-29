package com.doodle.doodlecodingchallenge.slot.dto;

import java.time.Instant;

import jakarta.validation.constraints.NotNull;

public record CreateSlotRequest(
        @NotNull Instant start,
        @NotNull Instant end) {
}
