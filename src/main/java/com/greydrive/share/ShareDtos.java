package com.greydrive.share;

import jakarta.validation.constraints.NotBlank;

import java.time.Instant;

public final class ShareDtos {

    private ShareDtos() {
    }

    public record CreateShareRequest(Long fileId, Long folderId, String permission,
                                     String code, Long expiresInSeconds) {
    }

    public record CreateShareResponse(Long id, String token, String url, String code,
                                      String permission, Instant expiresAt) {
    }

    public record ShareView(Long id, String token, Long fileId, Long folderId, String permission,
                            boolean hasCode, Instant expiresAt, String status, long downloadCount,
                            Instant createdAt) {
    }

    public record PublicShareInfo(String type, String name, Long fileId, Long folderId,
                                  String permission, boolean needCode, Instant expiresAt) {
    }

    public record VerifyShareRequest(String code) {
    }

    public record VerifyShareResponse(String shareToken, String permission, Instant expiresAt) {
    }

    public record ShareFileItem(Long id, String type, String name, long size, String mimeType,
                                Instant updatedAt) {
    }

    public record ShareDownload(java.nio.file.Path path, String fileName, String mimeType) {
    }
}
