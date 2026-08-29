package com.doodle.doodlecodingchallenge.common;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@WebMvcTest(ApiExceptionHandlerTest.ThrowingController.class)
@Import({ApiExceptionHandler.class, ApiExceptionHandlerTest.ThrowingController.class})
class ApiExceptionHandlerTest {

    @Autowired
    MockMvc mockMvc;

    record Req(@jakarta.validation.constraints.NotBlank String name) {}

    @RestController
    static class ThrowingController {

        @GetMapping("/boom/not-found")
        void notFound() {
            throw NotFoundException.of("Thing", 42);
        }

        @GetMapping("/boom/conflict")
        void conflict() {
            throw new ConflictException("time range overlaps existing busy time for: bob@example.com");
        }

        @GetMapping("/boom/bad-request")
        void badRequest() {
            throw new InvalidRequestException("end must be after start");
        }

        @GetMapping("/boom/param")
        void missingParam(@RequestParam String param) {
        }

        @PostMapping(path = "/boom/validation", consumes = "application/json")
        void validation(@jakarta.validation.Valid @org.springframework.web.bind.annotation.RequestBody Req request) {
        }
    }

    @Test
    void mapsNotFoundToProblemDetail() throws Exception {
        mockMvc.perform(get("/boom/not-found"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.title").value("Resource not found"))
            .andExpect(jsonPath("$.detail").value("Thing not found: 42"));
    }

    @Test
    void mapsConflictToProblemDetail() throws Exception {
        mockMvc.perform(get("/boom/conflict"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.title").value("Conflict"))
            .andExpect(jsonPath("$.detail").value("time range overlaps existing busy time for: bob@example.com"));
    }

    @Test
    void mapsInvalidRequestToProblemDetail() throws Exception {
        mockMvc.perform(get("/boom/bad-request"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.title").value("Invalid request"))
            .andExpect(jsonPath("$.detail").value("end must be after start"));
    }

    @Test
    void mapsMissingParameterToProblemDetail() throws Exception {
        mockMvc.perform(get("/boom/param"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.title").value("Invalid request"))
            .andExpect(jsonPath("$.detail").value("Missing required parameter: param"));
    }

    @Test
    void mapsBeanValidationToProblemDetailWithErrors() throws Exception {
        mockMvc.perform(post("/boom/validation")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.title").value("Invalid request"))
            .andExpect(jsonPath("$.errors[0].field").value("name"));
    }
}
