package com.doodle.doodlecodingchallenge;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;

class InfrastructureTests extends AbstractIntegrationTest {

    @Test
    void apiDocsListAllEndpoints() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.paths['/api/v1/slots/{slotId}/book']").exists())
            .andExpect(jsonPath("$.paths['/api/v1/users/{userId}/calendar']").exists())
            .andExpect(jsonPath("$.paths['/api/v1/meetings/{id}']").exists());
    }

    @Test
    void healthIsUp() throws Exception {
        mockMvc.perform(get("/actuator/health"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void prometheusEndpointExposesMetrics() throws Exception {
        mockMvc.perform(get("/actuator/prometheus"))
            .andExpect(status().isOk());
    }
}
