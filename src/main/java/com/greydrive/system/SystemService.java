package com.greydrive.system;

import com.greydrive.blob.BlobRepository;
import com.greydrive.file.CloudFileRepository;
import com.greydrive.storage.StoragePaths;
import com.greydrive.system.SystemDtos.HealthView;
import com.greydrive.system.SystemDtos.StorageStatsView;
import com.greydrive.system.SystemDtos.SystemInfoView;
import org.springframework.boot.web.context.WebServerApplicationContext;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.nio.file.FileStore;
import java.nio.file.Files;
import java.time.Instant;

@Service
public class SystemService {

    private static final String VERSION = "1.0.0";

    private final BlobRepository blobRepository;
    private final CloudFileRepository fileRepository;
    private final StoragePaths storagePaths;
    private final ApplicationContext applicationContext;

    public SystemService(BlobRepository blobRepository, CloudFileRepository fileRepository,
                         StoragePaths storagePaths, ApplicationContext applicationContext) {
        this.blobRepository = blobRepository;
        this.fileRepository = fileRepository;
        this.storagePaths = storagePaths;
        this.applicationContext = applicationContext;
    }

    public HealthView health() {
        return new HealthView("UP", Instant.now(), VERSION);
    }

    public SystemInfoView info() {
        int port = 8080;
        if (applicationContext instanceof WebServerApplicationContext web) {
            port = web.getWebServer().getPort();
        }
        return new SystemInfoView(
                "GreyDrive",
                VERSION,
                System.getProperty("java.version"),
                System.getProperty("os.name"),
                System.getProperty("os.arch"),
                port,
                ManagementFactory.getRuntimeMXBean().getUptime() / 1000,
                storagePaths.dataDir().toString());
    }

    public StorageStatsView storage() {
        long blobCount = blobRepository.count();
        long physical = blobRepository.sumSize();
        long logical = fileRepository.sumAllActiveSize();
        long fileCount = fileRepository.countByDeletedFalse();
        long total = 0;
        long free = 0;
        long usable = 0;
        try {
            FileStore store = Files.getFileStore(storagePaths.dataDir());
            total = store.getTotalSpace();
            free = store.getUnallocatedSpace();
            usable = store.getUsableSpace();
        } catch (IOException ignored) {
            // 无法读取磁盘信息时返回 0
        }
        return new StorageStatsView(blobCount, physical, logical, fileCount, total, free, usable,
                storagePaths.dataDir().toString());
    }
}
