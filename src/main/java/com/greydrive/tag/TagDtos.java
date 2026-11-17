package com.greydrive.tag;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;

public final class TagDtos {

    private TagDtos() {
    }

    public record TagView(Long id, String name, String color, Instant createdAt) {
    }

    public record CreateTagRequest(@NotBlank @Size(max = 64) String name,
                                   @Size(max = 16) String color) {
    }

    public record UpdateTagRequest(@Size(max = 64) String name, @Size(max = 16) String color) {
    }

    public record SetFileTagsRequest(@NotNull List<Long> tagIds) {
    }
}
