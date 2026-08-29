package com.doodle.doodlecodingchallenge.meeting;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.doodle.doodlecodingchallenge.meeting.dto.MeetingDto;
import com.doodle.doodlecodingchallenge.meeting.dto.ParticipantDto;

@WebMvcTest(MeetingController.class)
class MeetingControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    MeetingService meetingService;

    @Test
    void getReturnsMeetingWithParticipants() throws Exception {
        UUID meetingId = UUID.randomUUID();
        when(meetingService.get(meetingId)).thenReturn(new MeetingDto(meetingId, "Sync", null,
            UUID.randomUUID(), UUID.randomUUID(),
            Instant.parse("2026-09-01T10:00:00Z"), Instant.parse("2026-09-01T11:00:00Z"),
            Instant.now(), List.of(new ParticipantDto("Bob", "bob@example.com", null))));

        mockMvc.perform(get("/api/v1/meetings/" + meetingId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.title").value("Sync"))
            .andExpect(jsonPath("$.participants[0].email").value("bob@example.com"));
    }

    @Test
    void cancelReturns204() throws Exception {
        mockMvc.perform(delete("/api/v1/meetings/" + UUID.randomUUID()))
            .andExpect(status().isNoContent());
    }

    @Test
    void byParticipantReturnsPagedMeetings() throws Exception {
        UUID meetingId = UUID.randomUUID();
        when(meetingService.findByParticipant(any(), any())).thenReturn(
            new PageImpl<>(List.of(new MeetingDto(meetingId, "Sync", null,
                UUID.randomUUID(), UUID.randomUUID(),
                Instant.parse("2026-09-01T10:00:00Z"), Instant.parse("2026-09-01T11:00:00Z"),
                Instant.now(), List.of())), PageRequest.of(0, 20), 1));

        mockMvc.perform(get("/api/v1/meetings").param("participant", "bob@example.com"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content[0].id").value(meetingId.toString()));
    }
}
