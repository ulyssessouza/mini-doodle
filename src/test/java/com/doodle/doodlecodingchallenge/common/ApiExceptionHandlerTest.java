package com.doodle.doodlecodingchallenge.common;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.doodle.boomstub.ThrowingController;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ThrowingController.class)
@Import({ApiExceptionHandler.class, ThrowingController.class})
class ApiExceptionHandlerTest {

    @Autowired
    MockMvc mockMvc;

    @Test
    void mapsNotFoundToProblemDetail() throws Exception {
        mockMvc.perform(get("/boom/not-found"))
            .andExpect(status().isNotFound())
            .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.title").value("Resource not found"))
            .andExpect(jsonPath("$.detail").value("Thing not found: 42"));
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

    @Test
    void mapsDataIntegrityViolationToProblemDetail() throws Exception {
        mockMvc.perform(get("/boom/integrity"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.title").value("Conflict"))
            .andExpect(jsonPath("$.detail").value("Operation violates a data constraint"));
    }
}
