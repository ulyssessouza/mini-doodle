package com.doodle.doodlecodingchallenge.meeting.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.doodle.doodlecodingchallenge.meeting.Meeting;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "Meeting")
public record MeetingDto(UUID id, String title, String description, UUID organizerId, UUID slotId,
                         Instant start, Instant end, Instant createdAt,
                         List<ParticipantDto> participants) {

    public static MeetingDto from(Meeting meeting) {
        return new MeetingDto(meeting.getId(), meeting.getTitle(), meeting.getDescription(),
            meeting.getOrganizer().getId(), meeting.getSlot().getId(),
            meeting.getSlot().getStartsAt(), meeting.getSlot().getEndsAt(), meeting.getCreatedAt(),
            meeting.getParticipants().stream().map(ParticipantDto::from).toList());
    }
}
