package com.greydrive.version;

import com.greydrive.blob.BlobService;
import com.greydrive.common.BizException;
import com.greydrive.common.ErrorCode;
import com.greydrive.file.CloudFile;
import com.greydrive.file.FileService;
import com.greydrive.storage.StorageService;
import com.greydrive.version.VersionDtos.VersionView;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class VersionService {

    private final FileVersionRepository versionRepository;
    private final FileService fileService;
    private final StorageService storageService;

    public VersionService(FileVersionRepository versionRepository, FileService fileService,
                          StorageService storageService) {
        this.versionRepository = versionRepository;
        this.fileService = fileService;
        this.storageService = storageService;
    }

    @Transactional(readOnly = true)
    public List<VersionView> list(long userId, long fileId) {
        CloudFile file = fileService.requireFile(userId, fileId);
        return versionRepository.findByFileIdOrderByVersionNoDesc(fileId).stream()
                .map(v -> new VersionView(v.getVersionNo(), v.getSize(), v.getMimeType(), v.getNote(),
                        v.getCreatedAt(), v.getVersionNo() == file.getCurrentVersion()))
                .toList();
    }

    @Transactional(readOnly = true)
    public VersionContent content(long userId, long fileId, int versionNo) {
        CloudFile file = fileService.requireFile(userId, fileId);
        FileVersion version = versionRepository.findByFileIdAndVersionNo(fileId, versionNo)
                .orElseThrow(() -> new BizException(ErrorCode.VERSION_NOT_FOUND));
        return new VersionContent(file.getName(), version.getMimeType(),
                storageService.path(BlobService.storageKey(version.getSha256())));
    }

    @Transactional
    public VersionView rollback(long userId, long fileId, int versionNo) {
        FileVersion target = versionRepository.findByFileIdAndVersionNo(fileId, versionNo)
                .orElseThrow(() -> new BizException(ErrorCode.VERSION_NOT_FOUND));
        FileVersion created = fileService.addVersion(userId, fileId, target.getSha256(), target.getSize(),
                target.getMimeType(), "rollback to v" + versionNo);
        return new VersionView(created.getVersionNo(), created.getSize(), created.getMimeType(),
                created.getNote(), created.getCreatedAt(), true);
    }

    public record VersionContent(String fileName, String mimeType, java.nio.file.Path path) {
    }
}
