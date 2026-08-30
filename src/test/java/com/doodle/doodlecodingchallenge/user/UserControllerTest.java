package com.doodle.doodlecodingchallenge.user;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.doodle.doodlecodingchallenge.user.dto.CreateUserRequest;
import com.doodle.doodlecodingchallenge.user.dto.UserDto;

@WebMvcTest(UserController.class)
class UserControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    UserService userService;

    @Test
    void registerReturns201WithLocation() throws Exception {
        UUID id = UUID.randomUUID();
        when(userService.register(any(CreateUserRequest.class)))
            .thenReturn(new UserDto(id, "Alice", "alice@example.com", Instant.now()));

        mockMvc.perform(post("/api/v1/users")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Alice\",\"email\":\"alice@example.com\"}"))
            .andExpect(status().isCreated())
            .andExpect(header().string("Location", "/api/v1/users/" + id))
            .andExpect(jsonPath("$.name").value("Alice"));
    }

    @Test
    void registerWithInvalidEmailReturns400WithFieldError() throws Exception {
        mockMvc.perform(post("/api/v1/users")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Alice\",\"email\":\"not-an-email\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.title").value("Invalid request"))
            .andExpect(jsonPath("$.errors[0].field").value("email"));
    }
}
