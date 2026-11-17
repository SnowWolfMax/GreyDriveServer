package com.greydrive.schedule;

import com.greydrive.config.GreyDriveProperties;
import com.greydrive.recycle.RecycleService;
import com.greydrive.upload.UploadService;
import com.greydrive.user.RefreshTokenService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;

@Component
public class CleanupScheduler {

    private static final Logger log = LoggerFactory.getLogger(CleanupScheduler.class);

    private final UploadService uploadService;
    private final RecycleService recycleService;
    private final RefreshTokenService refreshTokenService;
    private final GreyDriveProperties properties;

    private volatile Instant lastChunkRun;
    private volatile Instant lastRecycleRun;
    private volatile Instant lastTokenRun;

    public CleanupScheduler(UploadService uploadService, RecycleService recycleService,
                            RefreshTokenService refreshTokenService,
                            GreyDriveProperties properties) {
        this.uploadService = uploadService;
        this.recycleService = recycleService;
        this.refreshTokenService = refreshTokenService;
        this.properties = properties;
    }

    @Scheduled(fixedDelay = 30_000, initialDelay = 30_000)
    public void tick() {
        if (!properties.getCleanup().isEnabled()) {
            return;
        }
        Instant now = Instant.now();
        runIfDue(now, true);
        runIfDue(now, false);
        runTokenCleanup(now);
    }

    private void runTokenCleanup(Instant now) {
        if (lastTokenRun != null && Duration.between(lastTokenRun, now).compareTo(Duration.ofDays(1)) < 0) {
            return;
        }
        lastTokenRun = now;
        try {
            int removed = refreshTokenService.cleanupExpired();
            if (removed > 0) {
                log.info("刷新令牌清理完成：{}", removed);
            }
        } catch (Exception e) {
            log.warn("刷新令牌清理失败", e);
        }
    }

    private void runIfDue(Instant now, boolean chunk) {
        Duration interval = chunk ? properties.getCleanup().getChunkInterval()
                : properties.getCleanup().getRecycleInterval();
        Instant last = chunk ? lastChunkRun : lastRecycleRun;
        if (last != null && Duration.between(last, now).compareTo(interval) < 0) {
            return;
        }
        if (chunk) {
            lastChunkRun = now;
            try {
                int expired = uploadService.cleanupExpired();
                int orphans = uploadService.cleanupOrphanChunkDirs();
                if (expired > 0 || orphans > 0) {
                    log.info("分片清理完成：过期会话 {}，孤儿目录 {}", expired, orphans);
                }
            } catch (Exception e) {
                log.warn("分片清理失败", e);
            }
        } else {
            lastRecycleRun = now;
            try {
                recycleService.purgeExpired();
            } catch (Exception e) {
                log.warn("回收站清理失败", e);
            }
        }
    }
}
