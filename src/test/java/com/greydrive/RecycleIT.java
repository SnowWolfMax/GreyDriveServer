package com.greydrive;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class RecycleIT extends BaseIT {

    @Test
    void softDeleteListRestore() throws Exception {
        String token = registerAndLogin(uniqueName("recycle"));
        UploadResult file = upload(token, null, "trash.txt", "trash".getBytes());

        mockMvc.perform(delete("/api/v1/files/" + file.fileId()).header(AUTH, "Bearer " + token))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/files/" + file.fileId()).header(AUTH, "Bearer " + token))
                .andExpect(status().isNotFound());

        JsonNode list = getJson("/api/v1/recycle?type=FILE", token).path("data");
        assertTrue(list.path("total").asInt() >= 1);

        ObjectNode restore = objectMapper.createObjectNode();
        restore.putArray("fileIds").add(file.fileId());
        restore.putArray("folderIds");
        mockMvc.perform(post("/api/v1/recycle/restore").header(AUTH, "Bearer " + token)
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(restore)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/files/" + file.fileId()).header(AUTH, "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    void folderDeleteRestoresChildren() throws Exception {
        String token = registerAndLogin(uniqueName("recyclefolder"));
        long folder = createFolder(token, "folder-trash");
        UploadResult file = upload(token, folder, "inside.txt", "inside".getBytes());

        mockMvc.perform(delete("/api/v1/folders/" + folder).header(AUTH, "Bearer " + token))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/files/" + file.fileId()).header(AUTH, "Bearer " + token))
                .andExpect(status().isNotFound());

        ObjectNode restore = objectMapper.createObjectNode();
        restore.putArray("fileIds");
        restore.putArray("folderIds").add(folder);
        mockMvc.perform(post("/api/v1/recycle/restore").header(AUTH, "Bearer " + token)
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(restore)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/files/" + file.fileId()).header(AUTH, "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    void purgeReleasesPhysicalBlob() throws Exception {
        String token = registerAndLogin(uniqueName("purge"));
        byte[] content = bytes(4096, 12345);
        UploadResult file = upload(token, null, "purge.bin", content);

        long before = getJson("/api/v1/system/storage", token).path("data").path("physicalBytes").asLong();

        mockMvc.perform(delete("/api/v1/files/" + file.fileId()).header(AUTH, "Bearer " + token))
                .andExpect(status().isOk());
        mockMvc.perform(delete("/api/v1/recycle/" + file.fileId() + "?type=FILE").header(AUTH, "Bearer " + token))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/files/" + file.fileId()).header(AUTH, "Bearer " + token))
                .andExpect(status().isNotFound());

        long after = getJson("/api/v1/system/storage", token).path("data").path("physicalBytes").asLong();
        assertEquals(before - content.length, after);
    }

    @Test
    void emptyRecycleBin() throws Exception {
        String token = registerAndLogin(uniqueName("emptyrecycle"));
        UploadResult a = upload(token, null, "a.txt", bytes(100, 1));
        UploadResult b = upload(token, null, "b.txt", bytes(100, 2));
        mockMvc.perform(delete("/api/v1/files/" + a.fileId()).header(AUTH, "Bearer " + token))
                .andExpect(status().isOk());
        mockMvc.perform(delete("/api/v1/files/" + b.fileId()).header(AUTH, "Bearer " + token))
                .andExpect(status().isOk());

        mockMvc.perform(delete("/api/v1/recycle").header(AUTH, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isNumber());

        JsonNode list = getJson("/api/v1/recycle?type=FILE", token).path("data");
        assertEquals(0, list.path("total").asInt());
    }
}
