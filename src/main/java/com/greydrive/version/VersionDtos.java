package com.greydrive.version;

import java.time.Instant;

public final class VersionDtos {

    private VersionDtos() {
    }

    public record VersionView(int versionNo, long size, String mimeType, String note,
                              Instant createdAt, boolean current) {
    }
}
