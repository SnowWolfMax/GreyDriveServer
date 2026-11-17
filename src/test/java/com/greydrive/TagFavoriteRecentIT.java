package com.greydrive;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class TagFavoriteRecentIT extends BaseIT {

    @Test
    void tagsFavoritesAndRecentFlow() throws Exception {
        String token = registerAndLogin(uniqueName("tags"));
        UploadResult file = upload(token, null, "tagged.txt", "tag me".getBytes());

        ObjectNode createTag = objectMapper.createObjectNode();
        createTag.put("name", "important");
        createTag.put("color", "#FF0000");
        JsonNode tag = postJson("/api/v1/tags", token, createTag).path("data");
        long tagId = tag.path("id").asLong();
        assertEquals("important", tag.path("name").asText());

        ObjectNode setTags = objectMapper.createObjectNode();
        setTags.putArray("tagIds").add(tagId);
        mockMvc.perform(put("/api/v1/files/" + file.fileId() + "/tags").header(AUTH, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(setTags)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1));

        JsonNode byTag = getJson("/api/v1/files?tagId=" + tagId, token).path("data");
        assertEquals(1, byTag.path("total").asInt());

        mockMvc.perform(put("/api/v1/files/" + file.fileId() + "/favorite").header(AUTH, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.favorite").value(true));

        JsonNode favorites = getJson("/api/v1/favorites", token).path("data");
        assertTrue(favorites.path("total").asInt() >= 1);

        mockMvc.perform(get("/api/v1/files/" + file.fileId()).header(AUTH, "Bearer " + token))
                .andExpect(status().isOk());
        JsonNode recent = getJson("/api/v1/recent", token).path("data");
        assertTrue(recent.path("total").asInt() >= 1);

        mockMvc.perform(delete("/api/v1/recent").header(AUTH, "Bearer " + token))
                .andExpect(status().isOk());
        assertEquals(0, getJson("/api/v1/recent", token).path("data").path("total").asInt());

        mockMvc.perform(delete("/api/v1/tags/" + tagId).header(AUTH, "Bearer " + token))
                .andExpect(status().isOk());
        assertEquals(0, getJson("/api/v1/files/" + file.fileId() + "/tags", token).path("data").size());
    }
}
