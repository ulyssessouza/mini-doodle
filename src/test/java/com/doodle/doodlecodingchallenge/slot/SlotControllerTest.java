package com.doodle.doodlecodingchallenge.slot;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
import org.springframework.data.domain.Page;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.doodle.doodlecodingchallenge.meeting.MeetingService;
import com.doodle.doodlecodingchallenge.meeting.dto.MeetingDto;
import com.doodle.doodlecodingchallenge.meeting.dto.ParticipantDto;
import com.doodle.doodlecodingchallenge.slot.dto.SlotDto;

@WebMvcTest(SlotController.class)
class SlotControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    SlotService slotService;

    @MockitoBean
    MeetingService meetingService;

    @Test
    void createReturns201WithLocation() throws Exception {
        UUID ownerId = UUID.randomUUID();
        UUID slotId = UUID.randomUUID();
        when(slotService.create(any(), any())).thenReturn(new SlotDto(slotId, ownerId,
            Instant.parse("2026-09-01T10:00:00Z"), Instant.parse("2026-09-01T11:00:00Z"),
            SlotStatus.FREE, null));

        mockMvc.perform(post("/api/v1/users/{ownerId}/slots", ownerId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"start\":\"2026-09-01T10:00:00Z\",\"end\":\"2026-09-01T11:00:00Z\"}"))
            .andExpect(status().isCreated())
            .andExpect(header().string("Location", "/api/v1/slots/" + slotId))
            .andExpect(jsonPath("$.status").value("FREE"));
    }

    @Test
    void createWithMissingFieldsReturns400WithFieldErrors() throws Exception {
        mockMvc.perform(post("/api/v1/users/{ownerId}/slots", UUID.randomUUID())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors.length()").value(2));
    }

    @Test
    void listReturnsPagedSlots() throws Exception {
        when(slotService.list(any(), any(), any(), any(), any())).thenReturn(Page.empty());

        mockMvc.perform(get("/api/v1/users/{ownerId}/slots", UUID.randomUUID())
                .param("from", "2026-09-01T00:00:00Z")
                .param("to", "2026-09-02T00:00:00Z"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content").isArray());
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

    @Test
    void deleteReturns204() throws Exception {
        mockMvc.perform(delete("/api/v1/slots/" + UUID.randomUUID()))
            .andExpect(status().isNoContent());
    }
}
