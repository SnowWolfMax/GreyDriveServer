package com.greydrive;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class OperationLogAndSystemIT extends BaseIT {

    @Test
    void operationLogsContainLogin() throws Exception {
        String token = registerAndLogin(uniqueName("oplog"));

        JsonNode logs = getJson("/api/v1/logs?size=50", token).path("data");
        assertTrue(logs.path("total").asInt() >= 1);
        boolean hasLogin = false;
        for (JsonNode item : logs.path("items")) {
            if ("LOGIN".equals(item.path("action").asText())) {
                hasLogin = true;
                break;
            }
        }
        assertTrue(hasLogin);
    }

    @Test
    void systemEndpoints() throws Exception {
        String token = registerAndLogin(uniqueName("system"));

        mockMvc.perform(get("/api/v1/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("UP"));

        JsonNode info = getJson("/api/v1/system/info", token).path("data");
        assertEquals("GreyDrive", info.path("name").asText());
        assertTrue(info.path("port").asInt() > 0);

        JsonNode storage = getJson("/api/v1/system/storage", token).path("data");
        assertTrue(storage.has("physicalBytes"));
        assertTrue(storage.has("logicalBytes"));
        assertTrue(storage.has("blobCount"));
        assertTrue(storage.has("diskTotalBytes"));

        mockMvc.perform(get("/v3/api-docs")).andExpect(status().isOk());
        mockMvc.perform(get("/swagger-ui/index.html")).andExpect(status().isOk());
    }
}
