package com.doodle.doodlecodingchallenge.calendar;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.doodle.doodlecodingchallenge.calendar.dto.CalendarEntryDto;
import com.doodle.doodlecodingchallenge.slot.SlotStatus;

@WebMvcTest(CalendarController.class)
class CalendarControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    CalendarService calendarService;

    @Test
    void viewReturnsEntries() throws Exception {
        UUID userId = UUID.randomUUID();
        when(calendarService.view(any(), any(), any(), any())).thenReturn(List.of(
            new CalendarEntryDto(UUID.randomUUID(),
                Instant.parse("2026-09-01T10:00:00Z"), Instant.parse("2026-09-01T11:00:00Z"),
                SlotStatus.BUSY, UUID.randomUUID(), "Sync")));

        mockMvc.perform(get("/api/v1/users/{userId}/calendar", userId)
                .param("from", "2026-09-01T00:00:00Z")
                .param("to", "2026-09-02T00:00:00Z"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].status").value("BUSY"))
            .andExpect(jsonPath("$[0].title").value("Sync"));
    }
}
