package com.doodle.doodlecodingchallenge.meeting.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ParticipantRequest(
        @NotBlank @Size(max = 255) String name,
        @NotBlank @Email String email) {
}
