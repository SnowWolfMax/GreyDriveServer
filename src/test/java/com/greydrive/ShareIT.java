package com.greydrive;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ShareIT extends BaseIT {

    @Test
    void shareVerifyDownloadAndCancel() throws Exception {
        String token = registerAndLogin(uniqueName("share"));
        byte[] content = "shared content".getBytes();
        UploadResult file = upload(token, null, "share.txt", content);

        ObjectNode create = objectMapper.createObjectNode();
        create.put("fileId", file.fileId());
        create.put("permission", "DOWNLOAD");
        create.put("code", "ABCD12");
        JsonNode share = postJson("/api/v1/shares", token, create).path("data");
        String shareToken = share.path("token").asText();
        assertTrue(share.path("expiresAt").isNull());

        JsonNode info = getJson("/api/v1/public/shares/" + shareToken, null).path("data");
        assertEquals("FILE", info.path("type").asText());
        assertTrue(info.path("needCode").asBoolean());

        mockMvc.perform(post("/api/v1/public/shares/" + shareToken + "/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"WRONG1\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(14003));

        JsonNode verified = postJson("/api/v1/public/shares/" + shareToken + "/verify", null,
                objectMapper.createObjectNode().put("code", "ABCD12")).path("data");
        String access = verified.path("shareToken").asText();

        byte[] downloaded = mockMvc.perform(get("/api/v1/public/shares/" + shareToken + "/files/"
                        + file.fileId() + "/download")
                        .header("X-Share-Token", access))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        assertArrayEquals(content, downloaded);

        long shareId = share.path("id").asLong();
        mockMvc.perform(delete("/api/v1/shares/" + shareId).header(AUTH, "Bearer " + token))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/public/shares/" + shareToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(14001));
    }

    @Test
    void viewOnlyShareCannotDownload() throws Exception {
        String token = registerAndLogin(uniqueName("shareview"));
        UploadResult file = upload(token, null, "view.txt", "view only".getBytes());

        ObjectNode create = objectMapper.createObjectNode();
        create.put("fileId", file.fileId());
        create.put("permission", "VIEW");
        create.put("code", "VIEW12");
        JsonNode share = postJson("/api/v1/shares", token, create).path("data");
        String shareToken = share.path("token").asText();

        JsonNode verified = postJson("/api/v1/public/shares/" + shareToken + "/verify", null,
                objectMapper.createObjectNode().put("code", "VIEW12")).path("data");

        mockMvc.perform(get("/api/v1/public/shares/" + shareToken + "/files/" + file.fileId() + "/download")
                        .header("X-Share-Token", verified.path("shareToken").asText()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(14004));
    }

    @Test
    void folderShareListsAndDownloadsSubtree() throws Exception {
        String token = registerAndLogin(uniqueName("sharefolder"));
        long folder = createFolder(token, "shared-folder");
        byte[] content = "inside folder".getBytes();
        UploadResult file = upload(token, folder, "inside.txt", content);

        ObjectNode create = objectMapper.createObjectNode();
        create.put("folderId", folder);
        create.put("permission", "DOWNLOAD");
        create.put("code", "FOLD12");
        JsonNode share = postJson("/api/v1/shares", token, create).path("data");
        String shareToken = share.path("token").asText();

        JsonNode verified = postJson("/api/v1/public/shares/" + shareToken + "/verify", null,
                objectMapper.createObjectNode().put("code", "FOLD12")).path("data");
        String access = verified.path("shareToken").asText();

        JsonNode list = read(mockMvc.perform(get("/api/v1/public/shares/" + shareToken + "/files")
                        .header("X-Share-Token", access))
                .andExpect(status().isOk()).andReturn()).path("data");
        assertEquals(1, list.path("total").asInt());
        assertEquals("inside.txt", list.path("items").get(0).path("name").asText());

        byte[] downloaded = mockMvc.perform(get("/api/v1/public/shares/" + shareToken + "/files/"
                        + file.fileId() + "/download").header("X-Share-Token", access))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        assertArrayEquals(content, downloaded);
    }

    @Test
    void expiredShareIsRejected() throws Exception {
        String token = registerAndLogin(uniqueName("shareexpire"));
        UploadResult file = upload(token, null, "expire.txt", "expire".getBytes());

        ObjectNode create = objectMapper.createObjectNode();
        create.put("fileId", file.fileId());
        create.put("code", "EXP123");
        create.put("expiresInSeconds", 1);
        String shareToken = postJson("/api/v1/shares", token, create).path("data").path("token").asText();

        Thread.sleep(1200);

        mockMvc.perform(get("/api/v1/public/shares/" + shareToken))
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.code").value(14002));
    }
}
