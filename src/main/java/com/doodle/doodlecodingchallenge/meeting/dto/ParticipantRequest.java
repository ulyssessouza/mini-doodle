package com.doodle.doodlecodingchallenge.meeting.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record ParticipantRequest(
        @NotBlank String name,
        @NotBlank @Email String email) {
}
