package com.greydrive.recycle;

import java.time.Instant;
import java.util.List;

public final class RecycleDtos {

    private RecycleDtos() {
    }

    public record RecycleItem(Long id, String type, String name, Long parentId, long size, Instant deletedAt) {
    }

    public record RestoreRequest(List<Long> fileIds, List<Long> folderIds) {
    }
}
