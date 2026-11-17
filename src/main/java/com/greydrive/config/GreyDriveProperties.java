package com.greydrive.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@ConfigurationProperties(prefix = "greydrive")
public class GreyDriveProperties {

    private String dataDir = "./data";
    /** 管理后台独立端口（与 server.port 并行监听） */
    private Admin admin = new Admin();
    private long chunkSize = 8L * 1024 * 1024;
    private long maxFileSize = 2L * 1024 * 1024 * 1024;
    private String storageType = "local";

    private Security security = new Security();
    private Upload upload = new Upload();
    private Recycle recycle = new Recycle();
    private Share share = new Share();
    private Preview preview = new Preview();
    private Pagination pagination = new Pagination();
    private Cleanup cleanup = new Cleanup();

    @Getter
    @Setter
    public static class Admin {
        private boolean enabled = true;
        private int port = 80;
    }

    @Getter
    @Setter
    public static class Security {
        private Duration jwtTtl = Duration.ofHours(24);
        private Duration refreshTtl = Duration.ofDays(30);
        private String jwtSecretFile = "./data/secrets/jwt.key";
        private Duration shareAccessTtl = Duration.ofMinutes(30);
        private boolean mfaRequired = true;
        private Duration mfaChallengeTtl = Duration.ofMinutes(5);
        private String mfaIssuer = "GreyDrive";
    }

    @Getter
    @Setter
    public static class Upload {
        private Duration sessionTtl = Duration.ofHours(24);
    }

    @Getter
    @Setter
    public static class Recycle {
        private Duration retention = Duration.ofDays(30);
    }

    @Getter
    @Setter
    public static class Share {
        /** 空表示永不过期 */
        private Duration defaultTtl;
        private int codeLength = 6;
        private boolean codeRequired = true;
    }

    @Getter
    @Setter
    public static class Preview {
        private long textMaxBytes = 1024L * 1024;
        private List<Integer> thumbnailSizes = new ArrayList<>(List.of(256, 1024));
    }

    @Getter
    @Setter
    public static class Pagination {
        private int defaultSize = 20;
        private int maxSize = 100;
    }

    @Getter
    @Setter
    public static class Cleanup {
        private boolean enabled = true;
        private Duration chunkInterval = Duration.ofMinutes(10);
        private Duration recycleInterval = Duration.ofHours(1);
    }
}
