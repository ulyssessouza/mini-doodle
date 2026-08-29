package com.doodle.doodlecodingchallenge.user;

import java.net.URI;
import java.util.UUID;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.doodle.doodlecodingchallenge.user.dto.CreateUserRequest;
import com.doodle.doodlecodingchallenge.user.dto.UserDto;

@RestController
@RequestMapping("/api/v1/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @PostMapping
    ResponseEntity<UserDto> register(@Valid @RequestBody CreateUserRequest request) {
        UserDto created = userService.register(request);
        return ResponseEntity.created(URI.create("/api/v1/users/" + created.id())).body(created);
    }

    @GetMapping("/{id}")
    UserDto get(@PathVariable UUID id) {
        return userService.get(id);
    }
}
