package com.greydrive.system;

import java.time.Instant;

public final class SystemDtos {

    private SystemDtos() {
    }

    public record HealthView(String status, Instant time, String version) {
    }

    public record SystemInfoView(String name, String version, String javaVersion, String osName,
                                 String osArch, int port, long uptimeSeconds, String dataDir) {
    }

    public record StorageStatsView(long blobCount, long physicalBytes, long logicalBytes, long fileCount,
                                   long diskTotalBytes, long diskFreeBytes, long diskUsableBytes,
                                   String dataDir) {
    }
}
