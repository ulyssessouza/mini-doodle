package com.doodle.doodlecodingchallenge.meeting.dto;

import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

public record BookRequest(
        @NotBlank @Size(max = 255) String title,
        @Size(max = 2048) String description,
        @NotEmpty List<ParticipantRequest> participants) {
}
