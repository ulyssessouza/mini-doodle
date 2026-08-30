package com.doodle.doodlecodingchallenge.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.doodle.doodlecodingchallenge.common.ConflictException;
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
}
