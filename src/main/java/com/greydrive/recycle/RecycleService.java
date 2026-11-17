package com.greydrive.recycle;

import com.greydrive.blob.BlobService;
import com.greydrive.common.BizException;
import com.greydrive.common.ErrorCode;
import com.greydrive.config.GreyDriveProperties;
import com.greydrive.file.CloudFile;
import com.greydrive.file.CloudFileRepository;
import com.greydrive.folder.Folder;
import com.greydrive.folder.FolderRepository;
import com.greydrive.recent.RecentAccessRepository;
import com.greydrive.recycle.RecycleDtos.RecycleItem;
import com.greydrive.tag.FileTagRepository;
import com.greydrive.version.FileVersion;
import com.greydrive.version.FileVersionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
public class RecycleService {

    private static final Logger log = LoggerFactory.getLogger(RecycleService.class);

    private final CloudFileRepository fileRepository;
    private final FolderRepository folderRepository;
    private final FileVersionRepository versionRepository;
    private final FileTagRepository fileTagRepository;
    private final RecentAccessRepository recentRepository;
    private final BlobService blobService;
    private final GreyDriveProperties properties;

    public RecycleService(CloudFileRepository fileRepository, FolderRepository folderRepository,
                          FileVersionRepository versionRepository, FileTagRepository fileTagRepository,
                          RecentAccessRepository recentRepository, BlobService blobService,
                          GreyDriveProperties properties) {
        this.fileRepository = fileRepository;
        this.folderRepository = folderRepository;
        this.versionRepository = versionRepository;
        this.fileTagRepository = fileTagRepository;
        this.recentRepository = recentRepository;
        this.blobService = blobService;
        this.properties = properties;
    }

    @Transactional(readOnly = true)
    public Page<RecycleItem> list(long userId, String type, Pageable pageable) {
        if ("FOLDER".equalsIgnoreCase(type)) {
            return folderRepository.findByUserIdAndDeletedTrue(userId, pageable)
                    .map(f -> new RecycleItem(f.getId(), "FOLDER", f.getName(), f.getParentId(), 0, f.getDeletedAt()));
        }
        return fileRepository.findByUserIdAndDeletedTrue(userId, pageable)
                .map(f -> new RecycleItem(f.getId(), "FILE", f.getName(), f.getFolderId(), f.getSize(), f.getDeletedAt()));
    }

    @Transactional
    public int restore(long userId, List<Long> fileIds, List<Long> folderIds) {
        boolean noFiles = fileIds == null || fileIds.isEmpty();
        boolean noFolders = folderIds == null || folderIds.isEmpty();
        if (noFiles && noFolders) {
            throw new BizException(ErrorCode.INVALID_PARAM, "至少选择一项恢复");
        }
        Instant now = Instant.now();
        int count = 0;
        if (folderIds != null) {
            for (Long id : folderIds) {
                Folder folder = folderRepository.findByIdAndUserId(id, userId)
                        .orElseThrow(() -> new BizException(ErrorCode.NOT_FOUND));
                if (folder.isDeleted()) {
                    restoreFolder(userId, folder, now);
                    count++;
                }
            }
        }
        if (fileIds != null) {
            for (Long id : fileIds) {
                CloudFile file = fileRepository.findByIdAndUserId(id, userId)
                        .orElseThrow(() -> new BizException(ErrorCode.NOT_FOUND));
                if (file.isDeleted()) {
                    restoreFile(userId, file, now);
                    count++;
                }
            }
        }
        return count;
    }

    @Transactional
    public void purge(long userId, String type, long id) {
        if ("FOLDER".equalsIgnoreCase(type)) {
            Folder folder = folderRepository.findByIdAndUserId(id, userId)
                    .filter(Folder::isDeleted)
                    .orElseThrow(() -> new BizException(ErrorCode.NOT_FOUND));
            purgeFolder(userId, folder);
        } else {
            CloudFile file = fileRepository.findByIdAndUserId(id, userId)
                    .filter(CloudFile::isDeleted)
                    .orElseThrow(() -> new BizException(ErrorCode.NOT_FOUND));
            purgeFile(file);
        }
    }

    @Transactional
    public int empty(long userId) {
        int count = 0;
        for (Folder folder : folderRepository.findByUserIdAndDeletedTrue(userId)) {
            if (folderRepository.existsById(folder.getId())) {
                purgeFolder(userId, folder);
                count++;
            }
        }
        for (CloudFile file : fileRepository.findByUserIdAndDeletedTrue(userId)) {
            if (fileRepository.existsById(file.getId())) {
                purgeFile(file);
                count++;
            }
        }
        return count;
    }

    @Transactional
    public int purgeExpired() {
        Instant cutoff = Instant.now().minus(properties.getRecycle().getRetention());
        int count = 0;
        for (Folder folder : folderRepository.findByDeletedTrueAndDeletedAtBefore(cutoff)) {
            if (folderRepository.existsById(folder.getId())) {
                purgeFolder(folder.getUserId(), folder);
                count++;
            }
        }
        for (CloudFile file : fileRepository.findByDeletedTrueAndDeletedAtBefore(cutoff)) {
            if (fileRepository.existsById(file.getId())) {
                purgeFile(file);
                count++;
            }
        }
        if (count > 0) {
            log.info("回收站超期清理完成，清理 {} 项", count);
        }
        return count;
    }

    private void restoreFile(long userId, CloudFile file, Instant now) {
        if (file.getFolderId() != null) {
            Folder parent = folderRepository.findByIdAndUserId(file.getFolderId(), userId).orElse(null);
            if (parent == null || parent.isDeleted()) {
                file.setFolderId(null);
            }
        }
        file.setDeleted(false);
        file.setDeletedAt(null);
        file.setUpdatedAt(now);
        fileRepository.save(file);
    }

    private void restoreFolder(long userId, Folder folder, Instant now) {
        if (!folder.isDeleted()) {
            return;
        }
        if (folder.getParentId() != null) {
            Folder parent = folderRepository.findByIdAndUserId(folder.getParentId(), userId).orElse(null);
            if (parent == null || parent.isDeleted()) {
                folder.setParentId(null);
            }
        }
        folder.setDeleted(false);
        folder.setDeletedAt(null);
        folder.setUpdatedAt(now);
        folderRepository.save(folder);

        for (CloudFile file : fileRepository.findByUserIdAndFolderId(userId, folder.getId())) {
            if (file.isDeleted()) {
                restoreFile(userId, file, now);
            }
        }
        for (Folder child : folderRepository.findByUserIdAndParentId(userId, folder.getId())) {
            if (child.isDeleted()) {
                restoreFolder(userId, child, now);
            }
        }
    }

    private void purgeFolder(long userId, Folder folder) {
        for (CloudFile file : fileRepository.findByUserIdAndFolderId(userId, folder.getId())) {
            purgeFile(file);
        }
        for (Folder child : folderRepository.findByUserIdAndParentId(userId, folder.getId())) {
            purgeFolder(userId, child);
        }
        folderRepository.delete(folder);
    }

    private void purgeFile(CloudFile file) {
        for (FileVersion version : versionRepository.findByFileIdOrderByVersionNoDesc(file.getId())) {
            blobService.release(version.getSha256());
        }
        versionRepository.deleteByFileId(file.getId());
        fileTagRepository.deleteByFileId(file.getId());
        recentRepository.deleteByFileId(file.getId());
        fileRepository.delete(file);
    }
}
