package com.greydrive;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class UploadAndFileIT extends BaseIT {

    @Test
    void multiChunkUploadResumeAndIdempotentComplete() throws Exception {
        String token = registerAndLogin(uniqueName("upload"));
        long folderId = createFolder(token, "uploads");
        byte[] content = bytes(2_621_440, 7); // 2.5 MiB

        UploadResult result = upload(token, folderId, "big.bin", content);
        assertTrue(result.fileId() > 0);
        assertEquals(1, result.versionNo());

        JsonNode file = getJson("/api/v1/files/" + result.fileId(), token).path("data");
        assertEquals(content.length, file.path("size").asLong());
        assertEquals("big.bin", file.path("name").asText());

        // 相同哈希再次上传：秒传命中
        UploadResult second = upload(token, null, "big-copy.bin", content);
        assertTrue(second.instant());
        assertNotEquals(result.fileId(), second.fileId());

        // 手动流程验证合并幂等
        String sha = com.greydrive.common.Hashing.sha256Hex(content);
        ObjectNode init = objectMapper.createObjectNode();
        init.put("fileName", "idempotent.bin");
        init.put("size", content.length);
        JsonNode data = postJson("/api/v1/uploads", token, init).path("data");
        String uploadId = data.path("uploadId").asText();
        int chunkSize = data.path("chunkSize").asInt();
        int total = data.path("totalChunks").asInt();
        for (int i = 0; i < total; i++) {
            int start = i * chunkSize;
            int end = Math.min(content.length, start + chunkSize);
            mockMvc.perform(put("/api/v1/uploads/" + uploadId + "/chunks/" + i)
                            .header(AUTH, "Bearer " + token)
                            .contentType(MediaType.APPLICATION_OCTET_STREAM)
                            .content(java.util.Arrays.copyOfRange(content, start, end)))
                    .andExpect(status().isOk());
        }
        JsonNode first = postJson("/api/v1/uploads/" + uploadId + "/complete", token,
                objectMapper.createObjectNode()).path("data");
        JsonNode again = postJson("/api/v1/uploads/" + uploadId + "/complete", token,
                objectMapper.createObjectNode()).path("data");
        assertEquals(first.path("fileId").asLong(), again.path("fileId").asLong());
        assertEquals(sha, first.path("sha256").asText());
    }

    @Test
    void missingChunkIsRejected() throws Exception {
        String token = registerAndLogin(uniqueName("chunkmissing"));
        byte[] content = bytes(2_000_000, 11);
        ObjectNode init = objectMapper.createObjectNode();
        init.put("fileName", "missing.bin");
        init.put("size", content.length);
        init.put("sha256", com.greydrive.common.Hashing.sha256Hex(content));
        init.put("chunkSize", 1024 * 1024);
        JsonNode data = postJson("/api/v1/uploads", token, init).path("data");
        String uploadId = data.path("uploadId").asText();

        mockMvc.perform(put("/api/v1/uploads/" + uploadId + "/chunks/0")
                        .header(AUTH, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_OCTET_STREAM)
                        .content(java.util.Arrays.copyOfRange(content, 0, 1024 * 1024)))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/uploads/" + uploadId + "/complete").header(AUTH, "Bearer " + token))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(13003));
    }

    @Test
    void searchRenameAndMove() throws Exception {
        String token = registerAndLogin(uniqueName("filesearch"));
        long folderA = createFolder(token, "A");
        long folderB = createFolder(token, "B");
        UploadResult uploaded = upload(token, folderA, "report-final.txt", "hello search".getBytes());
        upload(token, folderA, "other.txt", "other".getBytes());

        JsonNode search = getJson("/api/v1/files?keyword=report", token).path("data");
        assertEquals(1, search.path("total").asInt());
        assertEquals(uploaded.fileId(), search.path("items").get(0).path("id").asLong());

        JsonNode byFolder = getJson("/api/v1/files?folderId=" + folderA, token).path("data");
        assertEquals(2, byFolder.path("total").asInt());

        ObjectNode rename = objectMapper.createObjectNode();
        rename.put("name", "renamed.txt");
        mockMvc.perform(patch("/api/v1/files/" + uploaded.fileId()).header(AUTH, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(rename)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("renamed.txt"));

        ObjectNode move = objectMapper.createObjectNode();
        move.put("folderId", folderB);
        mockMvc.perform(post("/api/v1/files/" + uploaded.fileId() + "/move").header(AUTH, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(move)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.folderId").value(folderB));

        MvcResult result = mockMvc.perform(get("/api/v1/files?folderId=" + folderB).header(AUTH, "Bearer " + token))
                .andExpect(status().isOk()).andReturn();
        assertEquals(1, read(result).path("data").path("total").asInt());
    }
}
