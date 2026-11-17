package com.greydrive;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class DownloadRangeIT extends BaseIT {

    @Test
    void fullAndPartialDownload() throws Exception {
        String token = registerAndLogin(uniqueName("range"));
        byte[] content = "hello grey".getBytes();
        UploadResult file = upload(token, null, "range.txt", content);

        mockMvc.perform(get("/api/v1/files/" + file.fileId() + "/download").header(AUTH, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCEPT_RANGES, "bytes"))
                .andExpect(content().bytes(content));

        mockMvc.perform(get("/api/v1/files/" + file.fileId() + "/download").header(AUTH, "Bearer " + token)
                        .header(HttpHeaders.RANGE, "bytes=0-4"))
                .andExpect(status().isPartialContent())
                .andExpect(header().string(HttpHeaders.CONTENT_RANGE, "bytes 0-4/10"))
                .andExpect(content().bytes("hello".getBytes()));

        mockMvc.perform(get("/api/v1/files/" + file.fileId() + "/download").header(AUTH, "Bearer " + token)
                        .header(HttpHeaders.RANGE, "bytes=-5"))
                .andExpect(status().isPartialContent())
                .andExpect(header().string(HttpHeaders.CONTENT_RANGE, "bytes 5-9/10"))
                .andExpect(content().bytes(" grey".getBytes()));
    }

    @Test
    void invalidRangeReturns416() throws Exception {
        String token = registerAndLogin(uniqueName("range416"));
        UploadResult file = upload(token, null, "small.txt", "abc".getBytes());

        mockMvc.perform(get("/api/v1/files/" + file.fileId() + "/download").header(AUTH, "Bearer " + token)
                        .header(HttpHeaders.RANGE, "bytes=100-"))
                .andExpect(status().isRequestedRangeNotSatisfiable())
                .andExpect(header().string(HttpHeaders.CONTENT_RANGE, "bytes */3"));
    }

    @Test
    void unauthorizedDownloadRejected() throws Exception {
        String token = registerAndLogin(uniqueName("rangeauth"));
        UploadResult file = upload(token, null, "private.txt", "secret".getBytes());
        mockMvc.perform(get("/api/v1/files/" + file.fileId() + "/download"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void otherUserCannotDownload() throws Exception {
        String owner = registerAndLogin(uniqueName("owner"));
        String other = registerAndLogin(uniqueName("other"));
        UploadResult file = upload(owner, null, "owner.txt", "owner-data".getBytes());

        mockMvc.perform(get("/api/v1/files/" + file.fileId() + "/download").header(AUTH, "Bearer " + other))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(12002));
    }
}
