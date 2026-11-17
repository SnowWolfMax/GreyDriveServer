package com.greydrive.folder;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;

public final class FolderDtos {

    private FolderDtos() {
    }

    public record CreateFolderRequest(@NotBlank @Size(max = 255) String name, Long parentId) {
    }

    public record RenameFolderRequest(@NotBlank @Size(max = 255) String name) {
    }

    public record MoveFolderRequest(Long parentId) {
    }

    public record FolderView(Long id, Long parentId, String name, Instant createdAt, Instant updatedAt) {
    }

    public record BreadcrumbItem(Long id, String name) {
    }

    public record Breadcrumb(List<BreadcrumbItem> items) {
    }
}
