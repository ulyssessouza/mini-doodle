package com.doodle.doodlecodingchallenge;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import com.jayway.jsonpath.JsonPath;

class SchedulingFlowTests extends AbstractIntegrationTest {

    private String registerUser(String name, String email) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/users")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"%s\",\"email\":\"%s\"}".formatted(name, email)))
            .andExpect(status().isCreated())
            .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.id");
    }

    private String createSlot(String ownerId, String start, String end) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/users/{id}/slots", ownerId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"start\":\"%s\",\"end\":\"%s\"}".formatted(start, end)))
            .andExpect(status().isCreated())
            .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.id");
    }

    private int book(String slotId, String title, String... emails) throws Exception {
        StringBuilder participants = new StringBuilder();
        for (int i = 0; i < emails.length; i++) {
            if (i > 0) {
                participants.append(',');
            }
            participants.append("{\"name\":\"P").append(i).append("\",\"email\":\"")
                .append(emails[i]).append("\"}");
        }
        return mockMvc.perform(post("/api/v1/slots/{id}/book", slotId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"%s\",\"participants\":[%s]}".formatted(title, participants)))
            .andReturn()
            .getResponse()
            .getStatus();
    }

    private int patchSlot(String slotId, String body) throws Exception {
        return mockMvc.perform(patch("/api/v1/slots/" + slotId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andReturn()
            .getResponse()
            .getStatus();
    }

    private int deleteSlot(String slotId) throws Exception {
        return mockMvc.perform(delete("/api/v1/slots/" + slotId))
            .andReturn()
            .getResponse()
            .getStatus();
    }

    @Test
    void fullJourneyBookConflictCancelAndFreeAgain() throws Exception {
        String alice = registerUser("Alice", "alice@example.com");
        String bob = registerUser("Bob", "bob@example.com");
        String slotId = createSlot(alice, "2026-09-01T10:00:00Z", "2026-09-01T11:00:00Z");

        MvcResult booked = mockMvc.perform(post("/api/v1/slots/{id}/book", slotId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"title":"Design sync","description":"weekly",
                     "participants":[{"name":"Bob","email":"bob@example.com"},
                                     {"name":"Guest","email":"guest@example.com"}]}
                    """))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.participants.length()").value(2))
            .andExpect(jsonPath("$.participants[?(@.email=='bob@example.com')].userId")
                .value(org.hamcrest.Matchers.hasItem(bob)))
            .andReturn();
        String meetingId = JsonPath.read(booked.getResponse().getContentAsString(), "$.id");

        mockMvc.perform(get("/api/v1/slots/" + slotId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("BUSY"))
            .andExpect(jsonPath("$.meetingId").value(meetingId));

        mockMvc.perform(get("/api/v1/users/{id}/calendar", alice)
                .param("from", "2026-09-01T00:00:00Z").param("to", "2026-09-02T00:00:00Z")
                .param("status", "busy"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].meetingId").value(meetingId))
            .andExpect(jsonPath("$[0].title").value("Design sync"));

        mockMvc.perform(get("/api/v1/users/{id}/calendar", bob)
                .param("from", "2026-09-01T00:00:00Z").param("to", "2026-09-02T00:00:00Z")
                .param("status", "busy"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].meetingId").value(meetingId));

        String overlapping = createSlot(alice, "2026-09-01T10:30:00Z", "2026-09-01T11:30:00Z");
        mockMvc.perform(post("/api/v1/slots/{id}/book", overlapping)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"Clash\",\"participants\":[{\"name\":\"Bob\",\"email\":\"bob@example.com\"}]}"))
            .andExpect(status().isConflict());

        mockMvc.perform(delete("/api/v1/meetings/" + meetingId))
            .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/v1/slots/" + slotId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("FREE"))
            .andExpect(jsonPath("$.meetingId").value(org.hamcrest.Matchers.nullValue()));
    }

    @Test
    void participantMeetingListingReturnsBookedMeetings() throws Exception {
        String alice = registerUser("Alice5", "alice5@example.com");
        String bob = registerUser("Bob5", "bob5@example.com");
        String slotId = createSlot(alice, "2026-09-07T10:00:00Z", "2026-09-07T11:00:00Z");

        mockMvc.perform(post("/api/v1/slots/{id}/book", slotId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"title":"Sync","participants":[{"name":"Bob","email":"bob5@example.com"}]}
                    """))
            .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/meetings").param("participant", "BOB5@example.com"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content.length()").value(1))
            .andExpect(jsonPath("$.content[0].title").value("Sync"))
            .andExpect(jsonPath("$.content[0].slotId").value(slotId));
    }

    @Test
    void registeredParticipantConflictRejected() throws Exception {
        String alice = registerUser("Alice3", "alice3@example.com");
        String bob = registerUser("Bob3", "bob3@example.com");

        String bobsSlot = createSlot(bob, "2026-09-02T14:00:00Z", "2026-09-02T15:00:00Z");
        assertThat(patchSlot(bobsSlot, "{\"status\":\"BUSY\"}")).isEqualTo(200);

        String alicesSlot = createSlot(alice, "2026-09-02T14:30:00Z", "2026-09-02T15:30:00Z");
        assertThat(book(alicesSlot, "Overlap Bob", "bob3@example.com")).isEqualTo(409);
    }

    @Test
    void manualBusyBlocksOverlappingBookingForOwner() throws Exception {
        String eve = registerUser("Eve", "eve@example.com");
        String busySlot = createSlot(eve, "2026-09-03T10:00:00Z", "2026-09-03T11:00:00Z");
        assertThat(patchSlot(busySlot, "{\"status\":\"BUSY\"}")).isEqualTo(200);

        String overlapping = createSlot(eve, "2026-09-03T10:30:00Z", "2026-09-03T11:30:00Z");
        assertThat(book(overlapping, "Should clash", "guest@example.com")).isEqualTo(409);
    }

    @Test
    void guestsAreNotConflictChecked() throws Exception {
        String frank = registerUser("Frank", "frank@example.com");
        String frankBusy = createSlot(frank, "2026-09-04T10:00:00Z", "2026-09-04T11:00:00Z");
        assertThat(patchSlot(frankBusy, "{\"status\":\"BUSY\"}")).isEqualTo(200);

        String alice = registerUser("Alice4", "alice4@example.com");
        String alicesSlot = createSlot(alice, "2026-09-04T10:30:00Z", "2026-09-04T11:30:00Z");
        assertThat(book(alicesSlot, "Guests only", "stranger@example.com")).isEqualTo(201);
    }

    @Test
    void slotLifecycleRulesEnforced() throws Exception {
        String grace = registerUser("Grace", "grace@example.com");
        String slotId = createSlot(grace, "2026-09-05T09:00:00Z", "2026-09-05T10:00:00Z");

        assertThat(patchSlot(slotId,
            "{\"start\":\"2026-09-05T09:30:00Z\",\"end\":\"2026-09-05T10:30:00Z\"}")).isEqualTo(200);
        assertThat(patchSlot(slotId, "{\"status\":\"BUSY\"}")).isEqualTo(200);
        assertThat(patchSlot(slotId, "{\"start\":\"2026-09-05T08:00:00Z\",\"end\":\"2026-09-05T09:00:00Z\"}"))
            .isEqualTo(409);
        assertThat(deleteSlot(slotId)).isEqualTo(409);
        assertThat(patchSlot(slotId, "{\"status\":\"FREE\"}")).isEqualTo(200);
        assertThat(deleteSlot(slotId)).isEqualTo(204);
    }

    @Test
    void concurrentOverlappingBookingsExactlyOneSucceeds() throws Exception {
        String henry = registerUser("Henry", "henry@example.com");
        registerUser("Iris", "iris@example.com");
        String slot1 = createSlot(henry, "2026-09-06T10:00:00Z", "2026-09-06T11:00:00Z");
        String slot2 = createSlot(henry, "2026-09-06T10:30:00Z", "2026-09-06T11:30:00Z");

        CountDownLatch go = new CountDownLatch(1);
        Callable<Integer> first = () -> {
            go.await();
            return book(slot1, "Race A", "iris@example.com");
        };
        Callable<Integer> second = () -> {
            go.await();
            return book(slot2, "Race B", "iris@example.com");
        };
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<Integer> f1 = pool.submit(first);
            Future<Integer> f2 = pool.submit(second);
            go.countDown();
            List<Integer> statuses = List.of(f1.get(30, TimeUnit.SECONDS), f2.get(30, TimeUnit.SECONDS));
            assertThat(statuses).containsExactlyInAnyOrder(201, 409);
        } finally {
            pool.shutdownNow();
        }
    }
}
