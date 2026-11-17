package com.greydrive.upload;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;

public final class UploadDtos {

    private UploadDtos() {
    }

    public record InitUploadRequest(@NotBlank @Size(max = 255) String fileName,
                                    @NotNull @PositiveOrZero Long size,
                                    String sha256,
                                    Long folderId,
                                    Long fileId,
                                    Integer chunkSize) {
    }

    public record InitUploadResponse(String uploadId, int chunkSize, int totalChunks,
                                     List<Integer> uploadedChunks, boolean instant, Long fileId,
                                     Integer versionNo) {
    }

    public record UploadStatusResponse(String uploadId, String status, int chunkSize, int totalChunks,
                                       List<Integer> uploadedChunks, Instant expiresAt) {
    }

    public record ChunkResponse(int index, long size, String sha256, int uploadedChunks) {
    }

    public record CompleteUploadResponse(Long fileId, int versionNo, String sha256, long size) {
    }
}
