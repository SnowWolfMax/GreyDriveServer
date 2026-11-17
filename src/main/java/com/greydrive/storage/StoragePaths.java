package com.greydrive.storage;

import com.greydrive.config.GreyDriveProperties;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

@Component
public class StoragePaths {

    private final Path dataDir;
    private final Path blobsDir;
    private final Path chunksDir;
    private final Path thumbnailsDir;
    private final Path tmpDir;
    private final Path secretsDir;
    private final Path logsDir;

    public StoragePaths(GreyDriveProperties properties) {
        this.dataDir = Paths.get(properties.getDataDir()).toAbsolutePath().normalize();
        this.blobsDir = dataDir.resolve("storage/blobs");
        this.chunksDir = dataDir.resolve("chunks");
        this.thumbnailsDir = dataDir.resolve("thumbnails");
        this.tmpDir = dataDir.resolve("tmp");
        this.secretsDir = dataDir.resolve("secrets");
        this.logsDir = dataDir.resolve("logs");
        try {
            for (Path p : new Path[]{dataDir, dataDir.resolve("db"), blobsDir, chunksDir, thumbnailsDir, tmpDir, secretsDir, logsDir}) {
                Files.createDirectories(p);
            }
        } catch (IOException e) {
            throw new IllegalStateException("无法创建数据目录: " + dataDir, e);
        }
    }

    public Path dataDir() {
        return dataDir;
    }

    public Path blobsDir() {
        return blobsDir;
    }

    public Path chunksDir() {
        return chunksDir;
    }

    public Path thumbnailsDir() {
        return thumbnailsDir;
    }

    public Path tmpDir() {
        return tmpDir;
    }

    public Path secretsDir() {
        return secretsDir;
    }

    public Path logsDir() {
        return logsDir;
    }

    public Path blobPath(String sha256) {
        return blobsDir.resolve(sha256.substring(0, 2)).resolve(sha256.substring(2, 4)).resolve(sha256);
    }

    public Path chunkDir(String uploadId) {
        return chunksDir.resolve(uploadId);
    }

    public Path chunkPath(String uploadId, int index) {
        return chunkDir(uploadId).resolve(index + ".part");
    }

    public Path thumbnailPath(String sha256, int size) {
        return thumbnailsDir.resolve(sha256).resolve(size + ".jpg");
    }

    public Path newTempFile(String prefix) {
        return tmpDir.resolve(prefix + "-" + UUID.randomUUID());
    }
}
