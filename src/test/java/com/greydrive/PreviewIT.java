package com.greydrive;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class PreviewIT extends BaseIT {

    @Test
    void imageThumbnailAndTextPreview() throws Exception {
        String token = registerAndLogin(uniqueName("preview"));

        BufferedImage image = new BufferedImage(40, 40, BufferedImage.TYPE_INT_RGB);
        for (int x = 0; x < 40; x++) {
            for (int y = 0; y < 40; y++) {
                image.setRGB(x, y, (x * 6 + y * 3) & 0xFFFFFF);
            }
        }
        ByteArrayOutputStream png = new ByteArrayOutputStream();
        ImageIO.write(image, "png", png);
        UploadResult imageFile = upload(token, null, "picture.png", png.toByteArray());

        byte[] thumbnail = mockMvc.perform(get("/api/v1/files/" + imageFile.fileId() + "/thumbnail")
                        .param("size", "64").header(AUTH, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.IMAGE_JPEG))
                .andReturn().getResponse().getContentAsByteArray();
        assertTrue(thumbnail.length > 0);

        UploadResult textFile = upload(token, null, "note.txt", "hello preview text".getBytes());
        mockMvc.perform(get("/api/v1/files/" + textFile.fileId() + "/preview/text")
                        .header(AUTH, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content").value("hello preview text"))
                .andExpect(jsonPath("$.data.truncated").value(false));
    }

    @Test
    void unsupportedPreviewIsRejected() throws Exception {
        String token = registerAndLogin(uniqueName("previewbad"));
        UploadResult binary = upload(token, null, "data.bin", new byte[]{0, 1, 2, 3});

        mockMvc.perform(get("/api/v1/files/" + binary.fileId() + "/preview/text")
                        .header(AUTH, "Bearer " + token))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.code").value(18001));

        mockMvc.perform(get("/api/v1/files/" + binary.fileId() + "/thumbnail")
                        .header(AUTH, "Bearer " + token))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.code").value(18001));
    }
}
