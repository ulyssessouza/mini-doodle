package com.doodle.doodlecodingchallenge.meeting.dto;

import java.util.UUID;

import com.doodle.doodlecodingchallenge.meeting.MeetingParticipant;

public record ParticipantDto(String name, String email, UUID userId) {

    public static ParticipantDto from(MeetingParticipant participant) {
        return new ParticipantDto(participant.getDisplayName(), participant.getEmail(),
            participant.getUser() == null ? null : participant.getUser().getId());
    }
}
