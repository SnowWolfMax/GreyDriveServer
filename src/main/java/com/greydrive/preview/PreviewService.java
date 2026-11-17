package com.greydrive.preview;

import com.greydrive.blob.BlobService;
import com.greydrive.common.BizException;
import com.greydrive.common.ErrorCode;
import com.greydrive.common.MimeTypeUtil;
import com.greydrive.config.GreyDriveProperties;
import com.greydrive.file.CloudFile;
import com.greydrive.nativebridge.NativeImage;
import com.greydrive.storage.StoragePaths;
import com.greydrive.storage.StorageService;
import net.coobird.thumbnailator.Thumbnails;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

@Service
public class PreviewService {

    private static final Logger log = LoggerFactory.getLogger(PreviewService.class);

    private final StorageService storageService;
    private final StoragePaths storagePaths;
    private final GreyDriveProperties properties;

    public PreviewService(StorageService storageService, StoragePaths storagePaths, GreyDriveProperties properties) {
        this.storageService = storageService;
        this.storagePaths = storagePaths;
        this.properties = properties;
    }

    public Path thumbnail(CloudFile file, int size) {
        if (!MimeTypeUtil.isImage(file.getMimeType(), file.getName()) || file.getSha256() == null) {
            throw new BizException(ErrorCode.PREVIEW_UNSUPPORTED, "该文件不支持缩略图");
        }
        int edge = Math.max(64, Math.min(size, 2048));
        Path cached = storagePaths.thumbnailPath(file.getSha256(), edge);
        if (Files.isRegularFile(cached)) {
            return cached;
        }
        synchronized (PreviewService.class) {
            if (Files.isRegularFile(cached)) {
                return cached;
            }
            Path source = storageService.path(BlobService.storageKey(file.getSha256()));
            if (!Files.isRegularFile(source)) {
                throw new BizException(ErrorCode.STORAGE_ERROR, "文件内容缺失");
            }
            try {
                Files.createDirectories(cached.getParent());
                Path temp = cached.resolveSibling(cached.getFileName() + ".tmp");
                boolean generated = NativeImage.tryThumbnail(source, temp, edge)
                        && Files.isRegularFile(temp) && Files.size(temp) > 0;
                if (!generated) {
                    Files.deleteIfExists(temp);
                    try (OutputStream out = Files.newOutputStream(temp)) {
                        Thumbnails.of(source.toFile())
                                .size(edge, edge)
                                .outputFormat("jpg")
                                .outputQuality(0.85)
                                .toOutputStream(out);
                    }
                }
                Files.move(temp, cached, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException | IllegalArgumentException e) {
                log.warn("生成缩略图失败 fileId={}", file.getId(), e);
                throw new BizException(ErrorCode.PREVIEW_UNSUPPORTED, "无法生成缩略图");
            }
            return cached;
        }
    }

    public TextPreview text(CloudFile file) {
        if (!MimeTypeUtil.isText(file.getMimeType(), file.getName()) || file.getSha256() == null) {
            throw new BizException(ErrorCode.PREVIEW_UNSUPPORTED, "该文件不支持文本预览");
        }
        Path source = storageService.path(BlobService.storageKey(file.getSha256()));
        if (!Files.isRegularFile(source)) {
            throw new BizException(ErrorCode.STORAGE_ERROR, "文件内容缺失");
        }
        long max = Math.max(1024, properties.getPreview().getTextMaxBytes());
        try {
            long total = Files.size(source);
            int toRead = (int) Math.min(total, max);
            byte[] bytes = new byte[toRead];
            try (InputStream in = Files.newInputStream(source)) {
                int offset = 0;
                while (offset < toRead) {
                    int read = in.read(bytes, offset, toRead - offset);
                    if (read < 0) {
                        break;
                    }
                    offset += read;
                }
            }
            String content = new String(bytes, StandardCharsets.UTF_8);
            return new TextPreview(content, total > max, total, max);
        } catch (IOException e) {
            throw new BizException(ErrorCode.STORAGE_ERROR, "读取文本失败");
        }
    }

    public record TextPreview(String content, boolean truncated, long totalBytes, long maxBytes) {
    }
}
