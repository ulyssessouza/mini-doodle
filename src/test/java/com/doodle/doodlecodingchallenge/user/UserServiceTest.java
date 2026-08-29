package com.doodle.doodlecodingchallenge.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.doodle.doodlecodingchallenge.common.ConflictException;
import com.doodle.doodlecodingchallenge.common.NotFoundException;
import com.doodle.doodlecodingchallenge.user.dto.CreateUserRequest;
import com.doodle.doodlecodingchallenge.user.dto.UserDto;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    UserRepository users;

    @Test
    void registerCreatesUser() {
        UserService service = new UserService(users);
        when(users.existsByEmailIgnoreCase("alice@example.com")).thenReturn(false);
        when(users.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        UserDto dto = service.register(new CreateUserRequest("Alice", "alice@example.com"));

        assertThat(dto.name()).isEqualTo("Alice");
        assertThat(dto.email()).isEqualTo("alice@example.com");
        assertThat(dto.id()).isNotNull();
        assertThat(dto.createdAt()).isNotNull();
    }

    @Test
    void registerRejectsDuplicateEmail() {
        UserService service = new UserService(users);
        when(users.existsByEmailIgnoreCase("alice@example.com")).thenReturn(true);

        assertThatThrownBy(() -> service.register(new CreateUserRequest("Alice", "alice@example.com")))
            .isInstanceOf(ConflictException.class)
            .hasMessageContaining("alice@example.com");
    }

    @Test
    void getReturnsMappedUser() {
        UserService service = new UserService(users);
        User user = new User(UUID.randomUUID(), "Alice", "alice@example.com", Instant.now());
        when(users.findById(user.getId())).thenReturn(java.util.Optional.of(user));

        UserDto dto = service.get(user.getId());

        assertThat(dto.id()).isEqualTo(user.getId());
        assertThat(dto.email()).isEqualTo("alice@example.com");
    }

    @Test
    void getUnknownUserThrowsNotFound() {
        UserService service = new UserService(users);
        when(users.findById(any())).thenReturn(java.util.Optional.empty());

        assertThatThrownBy(() -> service.get(UUID.randomUUID()))
            .isInstanceOf(NotFoundException.class);
    }
}
