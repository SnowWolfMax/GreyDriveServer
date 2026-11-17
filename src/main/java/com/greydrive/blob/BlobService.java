package com.greydrive.blob;

import com.greydrive.common.BizException;
import com.greydrive.common.ErrorCode;
import com.greydrive.storage.StorageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;

@Service
public class BlobService {

    private static final Logger log = LoggerFactory.getLogger(BlobService.class);

    private final BlobRepository blobRepository;
    private final StorageService storageService;
    private final Object writeLock = new Object();

    public BlobService(BlobRepository blobRepository, StorageService storageService) {
        this.blobRepository = blobRepository;
        this.storageService = storageService;
    }

    /**
     * 将临时文件登记为 blob。若同哈希已存在，物理层会去重（保留既有文件）。
     */
    public Blob store(String sha256, long size, Path source) {
        String key = storageKey(sha256);
        synchronized (writeLock) {
            try {
                storageService.store(key, source);
            } catch (IOException e) {
                throw new BizException(ErrorCode.STORAGE_ERROR, "写入存储失败");
            }
            Blob blob = blobRepository.findById(sha256).orElse(null);
            if (blob == null) {
                blob = new Blob();
                blob.setSha256(sha256);
                blob.setSize(size);
                blob.setStorageKey(key);
                blob.setRefCount(0);
                blob.setCreatedAt(Instant.now());
                blob = blobRepository.save(blob);
            }
            return blob;
        }
    }

    @Transactional
    public void retain(String sha256) {
        blobRepository.incrementRef(sha256);
    }

    @Transactional
    public void release(String sha256) {
        synchronized (writeLock) {
            int updated = blobRepository.decrementRef(sha256);
            if (updated == 0) {
                return;
            }
            Integer refCount = blobRepository.findRefCount(sha256);
            if (refCount != null && refCount <= 0) {
                try {
                    storageService.delete(storageKey(sha256));
                } catch (IOException e) {
                    log.warn("删除物理文件失败, sha256={}", sha256, e);
                }
                blobRepository.deleteById(sha256);
            }
        }
    }

    public boolean exists(String sha256) {
        return blobRepository.existsById(sha256);
    }

    public java.util.Optional<Blob> find(String sha256) {
        return blobRepository.findById(sha256);
    }

    public static String storageKey(String sha256) {
        return sha256.substring(0, 2) + "/" + sha256.substring(2, 4) + "/" + sha256;
    }
}
