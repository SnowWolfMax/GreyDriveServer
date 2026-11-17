package com.greydrive;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.greydrive.common.Hashing;
import org.junit.jupiter.api.AfterAll;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Comparator;
import java.util.concurrent.atomic.AtomicInteger;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public abstract class BaseIT {

    protected static final String AUTH = "Authorization";
    protected static final String PASSWORD = "Passw0rd!23";

    private static final Path TEMP_DIR;
    private static final String DB_URL;
    private static final AtomicInteger SEQ = new AtomicInteger();

    static {
        try {
            TEMP_DIR = Files.createTempDirectory("greydrive-it-");
        } catch (IOException e) {
            throw new ExceptionInInitializerError(e);
        }
        DB_URL = "jdbc:h2:mem:greydrive_it;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE";
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> DB_URL);
        registry.add("greydrive.data-dir", TEMP_DIR::toString);
        registry.add("greydrive.security.jwt-secret-file", () -> TEMP_DIR.resolve("secrets/jwt.key").toString());
        registry.add("greydrive.cleanup.enabled", () -> "false");
        registry.add("logging.file.name", () -> TEMP_DIR.resolve("logs/test.log").toString());
    }

    @AfterAll
    static void cleanupTempDir() {
        // 保留临时目录便于失败排查；JVM 退出后由系统清理
    }

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected ObjectMapper objectMapper;

    protected String registerAndLogin(String username) throws Exception {
        ObjectNode register = objectMapper.createObjectNode();
        register.put("username", username);
        register.put("password", PASSWORD);
        register.put("nickname", "IT " + username);
        mockMvc.perform(MockMvcRequestBuilders.post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(register)))
                .andExpect(status().isOk());

        ObjectNode login = objectMapper.createObjectNode();
        login.put("username", username);
        login.put("password", PASSWORD);
        return postJson("/api/v1/auth/login", null, login).path("data").path("accessToken").asText();
    }

    protected JsonNode postJson(String path, String token, Object body) throws Exception {
        MockHttpServletRequestBuilder builder = MockMvcRequestBuilders.post(path)
                .contentType(MediaType.APPLICATION_JSON);
        if (token != null) {
            builder.header(AUTH, "Bearer " + token);
        }
        builder.content(objectMapper.writeValueAsString(body));
        return read(mockMvc.perform(builder).andExpect(status().isOk()).andReturn());
    }

    protected JsonNode getJson(String path, String token) throws Exception {
        MockHttpServletRequestBuilder builder = MockMvcRequestBuilders.get(path);
        if (token != null) {
            builder.header(AUTH, "Bearer " + token);
        }
        return read(mockMvc.perform(builder).andExpect(status().isOk()).andReturn());
    }

    protected JsonNode read(MvcResult result) throws Exception {
        MockHttpServletResponse response = result.getResponse();
        return objectMapper.readTree(response.getContentAsByteArray());
    }

    protected long createFolder(String token, String name) throws Exception {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("name", name);
        return postJson("/api/v1/folders", token, body).path("data").path("id").asLong();
    }

    protected UploadResult upload(String token, Long folderId, String fileName, byte[] content) throws Exception {
        return upload(token, folderId, null, fileName, content, null);
    }

    protected UploadResult upload(String token, Long folderId, Long fileId, String fileName, byte[] content,
                                  Integer chunkSize) throws Exception {
        ObjectNode init = objectMapper.createObjectNode();
        init.put("fileName", fileName);
        init.put("size", content.length);
        init.put("sha256", Hashing.sha256Hex(content));
        if (folderId != null) {
            init.put("folderId", folderId);
        }
        if (fileId != null) {
            init.put("fileId", fileId);
        }
        if (chunkSize != null) {
            init.put("chunkSize", chunkSize);
        }
        JsonNode data = postJson("/api/v1/uploads", token, init).path("data");
        if (data.path("instant").asBoolean()) {
            return new UploadResult(data.path("fileId").asLong(), data.path("versionNo").asInt(), content.length, true);
        }
        String uploadId = data.path("uploadId").asText();
        int cs = data.path("chunkSize").asInt();
        int total = data.path("totalChunks").asInt();
        for (int i = total - 1; i >= 0; i--) {
            int start = i * cs;
            int end = Math.min(content.length, start + cs);
            byte[] chunk = Arrays.copyOfRange(content, start, end);
            mockMvc.perform(MockMvcRequestBuilders.put("/api/v1/uploads/" + uploadId + "/chunks/" + i)
                            .header(AUTH, "Bearer " + token)
                            .contentType(MediaType.APPLICATION_OCTET_STREAM)
                            .content(chunk))
                    .andExpect(status().isOk());
        }
        JsonNode complete = postJson("/api/v1/uploads/" + uploadId + "/complete", token,
                objectMapper.createObjectNode()).path("data");
        return new UploadResult(complete.path("fileId").asLong(), complete.path("versionNo").asInt(),
                content.length, false);
    }

    protected static byte[] bytes(int size, int seed) {
        byte[] data = new byte[size];
        for (int i = 0; i < size; i++) {
            data[i] = (byte) ((i * 31 + seed) & 0xFF);
        }
        return data;
    }

    protected static String uniqueName(String prefix) {
        return prefix + "_" + SEQ.incrementAndGet() + "_" + System.nanoTime();
    }

    public record UploadResult(long fileId, int versionNo, long size, boolean instant) {
    }
}
