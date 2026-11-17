package com.greydrive.file;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.time.Instant;

public final class FileDtos {

    private FileDtos() {
    }

    public record FileView(Long id, Long folderId, String name, String ext, String mimeType, long size,
                           String sha256, int currentVersion, boolean favorite, Instant createdAt,
                           Instant updatedAt, Instant lastAccessedAt) {
    }

    public record RenameFileRequest(@NotBlank @Size(max = 255) String name) {
    }

    public record MoveFileRequest(Long folderId) {
    }

    public record CheckHashRequest(@NotBlank String sha256, @NotNull @PositiveOrZero Long size,
                                   @NotBlank @Size(max = 255) String fileName, Long folderId) {
    }

    public record CheckHashResponse(boolean hit, Long fileId) {
    }
}
