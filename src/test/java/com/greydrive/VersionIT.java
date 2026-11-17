package com.greydrive;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class VersionIT extends BaseIT {

    @Test
    void versionListDownloadAndRollback() throws Exception {
        String token = registerAndLogin(uniqueName("version"));
        byte[] v1 = "version one".getBytes();
        byte[] v2 = "version two is longer".getBytes();

        UploadResult file = upload(token, null, "doc.txt", v1);
        UploadResult v2Result = upload(token, null, file.fileId(), "doc.txt", v2, null);
        assertEquals(2, v2Result.versionNo());

        JsonNode versions = getJson("/api/v1/files/" + file.fileId() + "/versions", token).path("data");
        assertEquals(2, versions.size());
        assertTrue(versions.get(0).path("current").asBoolean());

        byte[] downloaded = mockMvc.perform(get("/api/v1/files/" + file.fileId() + "/versions/1/download")
                        .header(AUTH, "Bearer " + token))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        assertArrayEquals(v1, downloaded);

        JsonNode rollback = postJson("/api/v1/files/" + file.fileId() + "/versions/1/rollback", token,
                objectMapper.createObjectNode()).path("data");
        assertEquals(3, rollback.path("versionNo").asInt());

        byte[] current = mockMvc.perform(get("/api/v1/files/" + file.fileId() + "/download")
                        .header(AUTH, "Bearer " + token))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        assertArrayEquals(v1, current);

        JsonNode after = getJson("/api/v1/files/" + file.fileId() + "/versions", token).path("data");
        assertEquals(3, after.size());
    }

    @Test
    void versionDownloadSupportsRange() throws Exception {
        String token = registerAndLogin(uniqueName("versionrange"));
        UploadResult file = upload(token, null, "v.txt", "0123456789".getBytes());

        mockMvc.perform(get("/api/v1/files/" + file.fileId() + "/versions/1/download")
                        .header(AUTH, "Bearer " + token)
                        .header("Range", "bytes=2-5"))
                .andExpect(status().isPartialContent())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .header().string("Content-Range", "bytes 2-5/10"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .content().bytes("2345".getBytes()));
    }
}
