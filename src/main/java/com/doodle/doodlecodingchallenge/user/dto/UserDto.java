package com.doodle.doodlecodingchallenge.user.dto;

import java.time.Instant;
import java.util.UUID;

import com.doodle.doodlecodingchallenge.user.User;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "User")
public record UserDto(UUID id, String name, String email, Instant createdAt) {

    public static UserDto from(User user) {
        return new UserDto(user.getId(), user.getName(), user.getEmail(), user.getCreatedAt());
    }
}
