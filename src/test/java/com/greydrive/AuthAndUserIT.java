package com.greydrive;

import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class AuthAndUserIT extends BaseIT {

    @Test
    void registerLoginProfileAndAuthGuard() throws Exception {
        String username = uniqueName("auth");
        String token = registerAndLogin(username);

        mockMvc.perform(get("/api/v1/users/me").header(AUTH, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.username").value(username));

        mockMvc.perform(get("/api/v1/files"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(10002));
    }

    @Test
    void duplicateUsernameAndWrongPassword() throws Exception {
        String username = uniqueName("dup");
        registerAndLogin(username);

        ObjectNode register = objectMapper.createObjectNode();
        register.put("username", username);
        register.put("password", PASSWORD);
        mockMvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(register)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(11001));

        ObjectNode login = objectMapper.createObjectNode();
        login.put("username", username);
        login.put("password", "wrong-password");
        mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(login)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(11002));
    }

    @Test
    void changePassword() throws Exception {
        String username = uniqueName("pwd");
        String token = registerAndLogin(username);

        ObjectNode change = objectMapper.createObjectNode();
        change.put("oldPassword", PASSWORD);
        change.put("newPassword", "NewPassw0rd!23");
        mockMvc.perform(put("/api/v1/users/me/password").header(AUTH, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(change)))
                .andExpect(status().isOk());

        ObjectNode login = objectMapper.createObjectNode();
        login.put("username", username);
        login.put("password", "NewPassw0rd!23");
        mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(login)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty());
    }
}
