package com.doodle.doodlecodingchallenge.slot;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.doodle.doodlecodingchallenge.meeting.MeetingService;
import com.doodle.doodlecodingchallenge.meeting.dto.MeetingDto;
import com.doodle.doodlecodingchallenge.meeting.dto.ParticipantDto;

@WebMvcTest(SlotController.class)
class SlotControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    SlotService slotService;

    @MockitoBean
    MeetingService meetingService;

    @Test
    void createWithMissingFieldsReturns400WithFieldErrors() throws Exception {
        mockMvc.perform(post("/api/v1/users/{ownerId}/slots", UUID.randomUUID())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors.length()").value(2));
    }

    @Test
    void bookReturns201WithMeetingLocation() throws Exception {
        UUID slotId = UUID.randomUUID();
        UUID meetingId = UUID.randomUUID();
        when(meetingService.book(any(), any())).thenReturn(new MeetingDto(meetingId, "Sync",
            null, UUID.randomUUID(), slotId,
            Instant.parse("2026-09-01T10:00:00Z"), Instant.parse("2026-09-01T11:00:00Z"),
            Instant.now(), List.of(new ParticipantDto("Bob", "bob@example.com", null))));

        mockMvc.perform(post("/api/v1/slots/{id}/book", slotId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"Sync\",\"participants\":[{\"name\":\"Bob\",\"email\":\"bob@example.com\"}]}"))
            .andExpect(status().isCreated())
            .andExpect(header().string("Location", "/api/v1/meetings/" + meetingId));
    }
}
