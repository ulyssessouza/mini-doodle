package com.doodle.doodlecodingchallenge.slot.dto;

import java.time.Instant;

import com.doodle.doodlecodingchallenge.slot.SlotStatus;

public record UpdateSlotRequest(
        Instant start,
        Instant end,
        SlotStatus status) {
}
