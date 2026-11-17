package com.greydrive;

import com.greydrive.nativebridge.NativeImage;
import com.greydrive.nativebridge.NativeLibrary;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class NativeAccelerationTest {

    @Test
    void nativeThumbnailGeneratesJpeg() throws Exception {
        Assumptions.assumeTrue(NativeLibrary.isAvailable(),
                "native library not bundled for " + System.getProperty("os.name") + "/" + System.getProperty("os.arch"));

        BufferedImage image = new BufferedImage(1200, 800, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                image.setRGB(x, y, (x * 255 / image.getWidth()) << 16 | (y * 255 / image.getHeight()) << 8 | 64);
            }
        }
        Path source = Files.createTempFile("greydrive-thumb", ".jpg");
        Path target = Files.createTempFile("greydrive-thumb-out", ".jpg");
        try {
            ImageIO.write(image, "jpg", source.toFile());
            assertTrue(NativeImage.tryThumbnail(source, target, 300), "native thumbnail failed");
            BufferedImage thumbnail = ImageIO.read(target.toFile());
            assertTrue(thumbnail.getWidth() <= 300 && thumbnail.getHeight() <= 300,
                    "unexpected size: " + thumbnail.getWidth() + "x" + thumbnail.getHeight());
        } finally {
            Files.deleteIfExists(source);
            Files.deleteIfExists(target);
        }
    }
}
