package com.greydrive;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class FolderIT extends BaseIT {

    @Test
    void createListRenameBreadcrumbMoveAndDelete() throws Exception {
        String token = registerAndLogin(uniqueName("folder"));

        long parent = createFolder(token, "parent");
        long child = createFolder(token, "child");

        JsonNode rootList = getJson("/api/v1/folders", token).path("data");
        org.junit.jupiter.api.Assertions.assertTrue(rootList.path("total").asInt() >= 2);

        ObjectNode rename = objectMapper.createObjectNode();
        rename.put("name", "child-renamed");
        mockMvc.perform(patch("/api/v1/folders/" + child).header(AUTH, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(rename)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("child-renamed"));

        mockMvc.perform(get("/api/v1/folders/" + parent + "/path").header(AUTH, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].name").value("parent"));

        ObjectNode create = objectMapper.createObjectNode();
        create.put("name", "parent");
        mockMvc.perform(post("/api/v1/folders").header(AUTH, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(create)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(12003));

        mockMvc.perform(delete("/api/v1/folders/" + parent).header(AUTH, "Bearer " + token))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/recycle?type=FOLDER").header(AUTH, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(org.hamcrest.Matchers.greaterThanOrEqualTo(1)));
    }

    @Test
    void moveChildIntoItselfIsRejected() throws Exception {
        String token = registerAndLogin(uniqueName("foldermove"));
        long a = createFolder(token, "a");
        long b = createFolder(token, "b");

        ObjectNode move = objectMapper.createObjectNode();
        move.put("parentId", b);
        mockMvc.perform(post("/api/v1/folders/" + a + "/move").header(AUTH, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(move)))
                .andExpect(status().isOk());

        ObjectNode moveBack = objectMapper.createObjectNode();
        moveBack.put("parentId", a);
        mockMvc.perform(post("/api/v1/folders/" + a + "/move").header(AUTH, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(moveBack)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(12005));
    }
}
