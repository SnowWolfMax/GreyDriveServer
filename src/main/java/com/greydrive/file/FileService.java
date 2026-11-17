package com.greydrive.file;

import com.greydrive.blob.Blob;
import com.greydrive.blob.BlobService;
import com.greydrive.common.BizException;
import com.greydrive.common.ErrorCode;
import com.greydrive.common.Hashing;
import com.greydrive.common.MimeTypeUtil;
import com.greydrive.file.FileDtos.FileView;
import com.greydrive.folder.FolderService;
import com.greydrive.oplog.OperationLogService;
import com.greydrive.recent.RecentService;
import com.greydrive.tag.FileTagRepository;
import com.greydrive.version.FileVersion;
import com.greydrive.version.FileVersionRepository;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
public class FileService {

    private final CloudFileRepository fileRepository;
    private final FileVersionRepository versionRepository;
    private final FileTagRepository fileTagRepository;
    private final FolderService folderService;
    private final BlobService blobService;
    private final RecentService recentService;
    private final OperationLogService operationLogService;

    public FileService(CloudFileRepository fileRepository, FileVersionRepository versionRepository,
                       FileTagRepository fileTagRepository, FolderService folderService,
                       BlobService blobService, RecentService recentService,
                       OperationLogService operationLogService) {
        this.fileRepository = fileRepository;
        this.versionRepository = versionRepository;
        this.fileTagRepository = fileTagRepository;
        this.folderService = folderService;
        this.blobService = blobService;
        this.recentService = recentService;
        this.operationLogService = operationLogService;
    }

    @Transactional(readOnly = true)
    public Page<FileView> search(long userId, Long folderId, String keyword, String mimeType, String ext,
                                 Long minSize, Long maxSize, Long tagId, Boolean favorite,
                                 Instant from, Instant to, Pageable pageable) {
        Specification<CloudFile> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("userId"), userId));
            predicates.add(cb.isFalse(root.get("deleted")));
            if (folderId != null) {
                if (folderId == 0L) {
                    predicates.add(cb.isNull(root.get("folderId")));
                } else {
                    predicates.add(cb.equal(root.get("folderId"), folderId));
                }
            }
            if (keyword != null && !keyword.isBlank()) {
                predicates.add(cb.like(cb.lower(root.get("name")), "%" + keyword.toLowerCase(Locale.ROOT) + "%"));
            }
            if (mimeType != null && !mimeType.isBlank()) {
                predicates.add(cb.like(cb.lower(root.get("mimeType")), mimeType.toLowerCase(Locale.ROOT) + "%"));
            }
            if (ext != null && !ext.isBlank()) {
                predicates.add(cb.equal(cb.lower(root.get("ext")), ext.toLowerCase(Locale.ROOT)));
            }
            if (minSize != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("size"), minSize));
            }
            if (maxSize != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("size"), maxSize));
            }
            if (favorite != null) {
                predicates.add(cb.equal(root.get("favorite"), favorite));
            }
            if (from != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), from));
            }
            if (to != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("createdAt"), to));
            }
            if (tagId != null) {
                List<Long> fileIds = fileTagRepository.findFileIdsByTagId(tagId);
                if (fileIds.isEmpty()) {
                    predicates.add(cb.disjunction());
                } else {
                    predicates.add(root.get("id").in(fileIds));
                }
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        return fileRepository.findAll(spec, pageable).map(FileService::toView);
    }

    @Transactional
    public FileView get(long userId, long fileId) {
        CloudFile file = requireFile(userId, fileId);
        recentService.touch(userId, fileId);
        return toView(file);
    }

    @Transactional
    public FileView rename(long userId, long fileId, String name) {
        CloudFile file = requireFile(userId, fileId);
        String clean = sanitizeFileName(name);
        if (nameExists(userId, file.getFolderId(), clean, fileId)) {
            throw new BizException(ErrorCode.NAME_CONFLICT, "同名文件已存在");
        }
        file.setName(clean);
        file.setExt(MimeTypeUtil.extension(clean));
        file.setUpdatedAt(Instant.now());
        file = fileRepository.save(file);
        operationLogService.record(userId, "FILE_RENAME", "FILE", fileId, clean, currentIp(), currentUa(), true);
        return toView(file);
    }

    @Transactional
    public FileView move(long userId, long fileId, Long folderId) {
        CloudFile file = requireFile(userId, fileId);
        if (folderId != null) {
            folderService.requireFolder(folderId, userId);
        }
        if (nameExists(userId, folderId, file.getName(), fileId)) {
            throw new BizException(ErrorCode.NAME_CONFLICT, "目标目录存在同名文件");
        }
        file.setFolderId(folderId);
        file.setUpdatedAt(Instant.now());
        file = fileRepository.save(file);
        operationLogService.record(userId, "FILE_MOVE", "FILE", fileId, "folderId=" + folderId, currentIp(), currentUa(), true);
        return toView(file);
    }

    @Transactional
    public void softDelete(long userId, long fileId) {
        CloudFile file = requireFile(userId, fileId);
        file.setDeleted(true);
        file.setDeletedAt(Instant.now());
        file.setUpdatedAt(Instant.now());
        fileRepository.save(file);
        operationLogService.record(userId, "FILE_DELETE", "FILE", fileId, file.getName(), currentIp(), currentUa(), true);
    }

    @Transactional
    public FileView setFavorite(long userId, long fileId, boolean favorite) {
        CloudFile file = requireFile(userId, fileId);
        file.setFavorite(favorite);
        file.setUpdatedAt(Instant.now());
        file = fileRepository.save(file);
        operationLogService.record(userId, favorite ? "FILE_FAVORITE" : "FILE_UNFAVORITE", "FILE", fileId,
                null, currentIp(), currentUa(), true);
        return toView(file);
    }

    @Transactional(readOnly = true)
    public Page<FileView> favorites(long userId, Pageable pageable) {
        return fileRepository.findByUserIdAndFavoriteTrueAndDeletedFalse(userId, pageable).map(FileService::toView);
    }

    /**
     * 秒传：按 sha256 查重，命中已有 blob 则直接创建文件引用，不重复写盘。
     */
    @Transactional
    public FileDtos.CheckHashResponse instantUpload(long userId, String sha256, long size, String fileName, Long folderId) {
        if (!Hashing.isSha256(sha256)) {
            throw new BizException(ErrorCode.INVALID_PARAM, "sha256 格式不正确");
        }
        Blob blob = blobService.find(sha256.toLowerCase(Locale.ROOT)).orElse(null);
        if (blob == null || blob.getSize() != size) {
            return new FileDtos.CheckHashResponse(false, null);
        }
        String name = sanitizeFileName(fileName);
        CloudFile file = createFile(userId, folderId, name, sha256.toLowerCase(Locale.ROOT), size,
                MimeTypeUtil.guess(name), userId);
        operationLogService.record(userId, "FILE_INSTANT_UPLOAD", "FILE", file.getId(), name,
                currentIp(), currentUa(), true);
        return new FileDtos.CheckHashResponse(true, file.getId());
    }

    @Transactional
    public CloudFile createFile(long userId, Long folderId, String fileName, String sha256, long size,
                               String mimeType, Long createdBy) {
        if (folderId != null) {
            folderService.requireFolder(folderId, userId);
        }
        String name = uniqueFileName(userId, folderId, sanitizeFileName(fileName));
        Instant now = Instant.now();
        CloudFile file = new CloudFile();
        file.setUserId(userId);
        file.setFolderId(folderId);
        file.setName(name);
        file.setExt(MimeTypeUtil.extension(name));
        file.setMimeType(mimeType);
        file.setSize(size);
        file.setSha256(sha256);
        file.setCurrentVersion(1);
        file.setFavorite(false);
        file.setDeleted(false);
        file.setCreatedAt(now);
        file.setUpdatedAt(now);
        blobService.retain(sha256);
        file = fileRepository.save(file);

        FileVersion version = new FileVersion();
        version.setFileId(file.getId());
        version.setVersionNo(1);
        version.setSha256(sha256);
        version.setSize(size);
        version.setMimeType(mimeType);
        version.setCreatedBy(createdBy);
        version.setCreatedAt(now);
        versionRepository.save(version);
        return file;
    }

    @Transactional
    public FileVersion addVersion(long userId, long fileId, String sha256, long size, String mimeType, String note) {
        CloudFile file = requireFile(userId, fileId);
        int next = versionRepository.maxVersionNo(fileId) + 1;
        Instant now = Instant.now();
        blobService.retain(sha256);

        FileVersion version = new FileVersion();
        version.setFileId(fileId);
        version.setVersionNo(next);
        version.setSha256(sha256);
        version.setSize(size);
        version.setMimeType(mimeType);
        version.setNote(note);
        version.setCreatedBy(userId);
        version.setCreatedAt(now);
        version = versionRepository.save(version);

        file.setSize(size);
        file.setSha256(sha256);
        file.setMimeType(mimeType == null ? file.getMimeType() : mimeType);
        file.setCurrentVersion(next);
        file.setUpdatedAt(now);
        fileRepository.save(file);
        return version;
    }

    @Transactional(readOnly = true)
    public CloudFile requireFile(long userId, long fileId) {
        return fileRepository.findByIdAndUserIdAndDeletedFalse(fileId, userId)
                .orElseThrow(() -> new BizException(ErrorCode.FILE_NOT_FOUND));
    }

    public static FileView toView(CloudFile file) {
        return new FileView(file.getId(), file.getFolderId(), file.getName(), file.getExt(), file.getMimeType(),
                file.getSize(), file.getSha256(), file.getCurrentVersion(), file.isFavorite(),
                file.getCreatedAt(), file.getUpdatedAt(), file.getLastAccessedAt());
    }

    private boolean nameExists(long userId, Long folderId, String name, Long excludeId) {
        List<CloudFile> siblings = folderId == null
                ? fileRepository.findByUserIdAndFolderIdIsNullAndDeletedFalse(userId)
                : fileRepository.findByUserIdAndFolderIdAndDeletedFalse(userId, folderId);
        return siblings.stream().anyMatch(f -> f.getName().equalsIgnoreCase(name)
                && (excludeId == null || !f.getId().equals(excludeId)));
    }

    private String uniqueFileName(long userId, Long folderId, String name) {
        if (!nameExists(userId, folderId, name, null)) {
            return name;
        }
        int dot = name.lastIndexOf('.');
        String base = dot > 0 ? name.substring(0, dot) : name;
        String extension = dot > 0 ? name.substring(dot) : "";
        for (int i = 1; i < 10000; i++) {
            String candidate = base + " (" + i + ")" + extension;
            if (!nameExists(userId, folderId, candidate, null)) {
                return candidate;
            }
        }
        throw new BizException(ErrorCode.NAME_CONFLICT, "无法生成唯一文件名");
    }

    private String sanitizeFileName(String name) {
        String clean = name == null ? "" : name.trim();
        clean = clean.replace("/", "_").replace("\\", "_");
        if (clean.isEmpty()) {
            clean = "unnamed";
        }
        if (clean.length() > 255) {
            clean = clean.substring(0, 255);
        }
        return clean;
    }

    private static String currentIp() {
        return com.greydrive.common.WebRequestInfo.clientIp();
    }

    private static String currentUa() {
        return com.greydrive.common.WebRequestInfo.userAgent();
    }
}
