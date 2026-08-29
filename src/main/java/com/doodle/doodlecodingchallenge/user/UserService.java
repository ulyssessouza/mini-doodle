package com.doodle.doodlecodingchallenge.user;

import java.time.Instant;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.doodle.doodlecodingchallenge.common.ConflictException;
import com.doodle.doodlecodingchallenge.common.NotFoundException;
import com.doodle.doodlecodingchallenge.user.dto.CreateUserRequest;
import com.doodle.doodlecodingchallenge.user.dto.UserDto;

@Service
public class UserService {

    private final UserRepository users;

    public UserService(UserRepository users) {
        this.users = users;
    }

    @Transactional
    public UserDto register(CreateUserRequest request) {
        if (users.existsByEmailIgnoreCase(request.email())) {
            throw new ConflictException("A user with email %s already exists".formatted(request.email()));
        }
        User user = new User(UUID.randomUUID(), request.name(), request.email(), Instant.now());
        return UserDto.from(users.save(user));
    }

    @Transactional(readOnly = true)
    public UserDto get(UUID id) {
        return users.findById(id)
            .map(UserDto::from)
            .orElseThrow(() -> NotFoundException.of("User", id));
    }
}
