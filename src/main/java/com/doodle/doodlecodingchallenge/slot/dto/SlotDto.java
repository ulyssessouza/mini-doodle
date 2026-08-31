package com.doodle.doodlecodingchallenge.slot.dto;

import java.time.Instant;
import java.util.UUID;

import com.doodle.doodlecodingchallenge.slot.Slot;
import com.doodle.doodlecodingchallenge.slot.SlotStatus;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "Slot")
public record SlotDto(UUID id, UUID ownerId, Instant start, Instant end, SlotStatus status, UUID meetingId) {

    public static SlotDto from(Slot slot) {
        return new SlotDto(slot.getId(), slot.getOwner().getId(),
            slot.getStartsAt(), slot.getEndsAt(), slot.getStatus(),
            slot.getMeeting() == null ? null : slot.getMeeting().getId());
    }
}
