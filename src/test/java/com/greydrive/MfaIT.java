package com.greydrive;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.greydrive.security.totp.Totp;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class MfaIT extends BaseIT {

    @Test
    void totpSetupChallengeRecoveryAndDisable() throws Exception {
        String username = uniqueName("mfa");
        String token = registerAndLogin(username);

        assertFalse(getJson("/api/v1/users/me/mfa", token).path("data").path("enabled").asBoolean());

        JsonNode setup = postJson("/api/v1/auth/mfa/setup", token, objectMapper.createObjectNode()).path("data");
        String secret = setup.path("secret").asText();
        assertTrue(secret.length() >= 16);
        assertTrue(setup.path("qrDataUrl").asText().startsWith("data:image/png;base64,"));

        String enableCode = Totp.generate(secret, System.currentTimeMillis());
        JsonNode enabled = postJson("/api/v1/auth/mfa/enable", token,
                objectMapper.createObjectNode().put("code", enableCode)).path("data");
        assertEquals(10, enabled.path("recoveryCodes").size());
        String recoveryCode = enabled.path("recoveryCodes").get(0).asText();
        assertFalse(enabled.path("accessToken").asText().isBlank());

        // 已启用 MFA：密码登录返回二次验证挑战
        JsonNode login = postJson("/api/v1/auth/login", null, loginBody(username)).path("data");
        assertTrue(login.path("mfaRequired").asBoolean());
        assertTrue(login.path("accessToken").isNull());
        String mfaToken = login.path("mfaToken").asText();

        JsonNode verified = postJson("/api/v1/auth/mfa/verify", null,
                objectMapper.createObjectNode().put("mfaToken", mfaToken)
                        .put("code", Totp.generate(secret, System.currentTimeMillis()))).path("data");
        String jwt = verified.path("accessToken").asText();
        assertFalse(jwt.isBlank());

        // 使用恢复码登录
        JsonNode login2 = postJson("/api/v1/auth/login", null, loginBody(username)).path("data");
        JsonNode verified2 = postJson("/api/v1/auth/mfa/verify", null,
                objectMapper.createObjectNode().put("mfaToken", login2.path("mfaToken").asText())
                        .put("code", recoveryCode)).path("data");
        String jwt2 = verified2.path("accessToken").asText();
        assertFalse(jwt2.isBlank());

        JsonNode status = getJson("/api/v1/users/me/mfa", jwt2).path("data");
        assertTrue(status.path("enabled").asBoolean());
        assertEquals(9, status.path("unusedRecoveryCodes").asLong());

        // 关闭 MFA 后恢复单因素登录
        ObjectNode disable = objectMapper.createObjectNode();
        disable.put("password", PASSWORD);
        disable.put("code", Totp.generate(secret, System.currentTimeMillis()));
        mockMvc.perform(post("/api/v1/users/me/mfa/disable").header(AUTH, "Bearer " + jwt2)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(disable)))
                .andExpect(status().isOk());

        JsonNode direct = postJson("/api/v1/auth/login", null, loginBody(username)).path("data");
        assertFalse(direct.path("accessToken").asText().isBlank());
    }

    private ObjectNode loginBody(String username) {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("username", username);
        body.put("password", PASSWORD);
        return body;
    }
}
