package com.doodle.doodlecodingchallenge.calendar.dto;

import java.time.Instant;
import java.util.UUID;

import com.doodle.doodlecodingchallenge.meeting.Meeting;
import com.doodle.doodlecodingchallenge.slot.Slot;
import com.doodle.doodlecodingchallenge.slot.SlotStatus;

public record CalendarEntryDto(
        UUID slotId,
        Instant start,
        Instant end,
        SlotStatus status,
        UUID meetingId,
        String title) {

    public static CalendarEntryDto from(Slot slot) {
        Meeting meeting = slot.getMeeting();
        return new CalendarEntryDto(slot.getId(), slot.getStartsAt(), slot.getEndsAt(),
            slot.getStatus(),
            meeting == null ? null : meeting.getId(),
            meeting == null ? null : meeting.getTitle());
    }

    public static CalendarEntryDto from(Meeting meeting) {
        return new CalendarEntryDto(meeting.getSlot().getId(),
            meeting.getSlot().getStartsAt(), meeting.getSlot().getEndsAt(),
            SlotStatus.BUSY, meeting.getId(), meeting.getTitle());
    }
}
