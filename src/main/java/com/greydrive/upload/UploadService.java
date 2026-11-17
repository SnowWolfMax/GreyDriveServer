package com.greydrive.upload;

import com.greydrive.blob.Blob;
import com.greydrive.blob.BlobService;
import com.greydrive.common.BizException;
import com.greydrive.common.ErrorCode;
import com.greydrive.common.Hashing;
import com.greydrive.common.MimeTypeUtil;
import com.greydrive.config.GreyDriveProperties;
import com.greydrive.file.CloudFile;
import com.greydrive.file.CloudFileRepository;
import com.greydrive.file.FileService;
import com.greydrive.folder.FolderService;
import com.greydrive.storage.StoragePaths;
import com.greydrive.upload.UploadDtos.*;
import com.greydrive.version.FileVersion;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
public class UploadService {

    private static final Logger log = LoggerFactory.getLogger(UploadService.class);

    private final UploadSessionRepository sessionRepository;
    private final UploadChunkRepository chunkRepository;
    private final CloudFileRepository fileRepository;
    private final FileService fileService;
    private final FolderService folderService;
    private final BlobService blobService;
    private final StoragePaths storagePaths;
    private final GreyDriveProperties properties;

    public UploadService(UploadSessionRepository sessionRepository, UploadChunkRepository chunkRepository,
                         CloudFileRepository fileRepository, FileService fileService, FolderService folderService,
                         BlobService blobService, StoragePaths storagePaths, GreyDriveProperties properties) {
        this.sessionRepository = sessionRepository;
        this.chunkRepository = chunkRepository;
        this.fileRepository = fileRepository;
        this.fileService = fileService;
        this.folderService = folderService;
        this.blobService = blobService;
        this.storagePaths = storagePaths;
        this.properties = properties;
    }

    @Transactional
    public InitUploadResponse init(long userId, InitUploadRequest request) {
        if (request.size() > properties.getMaxFileSize()) {
            throw new BizException(ErrorCode.TOO_LARGE, "超过单文件大小上限");
        }
        String fileName = sanitize(request.fileName());
        if (request.folderId() != null) {
            folderService.requireFolder(request.folderId(), userId);
        }
        if (request.fileId() != null) {
            fileService.requireFile(userId, request.fileId());
        }
        String sha = request.sha256() == null || request.sha256().isBlank()
                ? null : request.sha256().trim().toLowerCase(Locale.ROOT);
        if (sha != null && !Hashing.isSha256(sha)) {
            throw new BizException(ErrorCode.INVALID_PARAM, "sha256 格式不正确");
        }

        if (sha != null) {
            Blob blob = blobService.find(sha).orElse(null);
            if (blob != null && blob.getSize() == request.size()) {
                if (request.fileId() != null) {
                    FileVersion version = fileService.addVersion(userId, request.fileId(), sha, request.size(),
                            MimeTypeUtil.guess(fileName), "instant upload");
                    return new InitUploadResponse(null, 0, 0, List.of(), true, request.fileId(), version.getVersionNo());
                }
                CloudFile file = fileService.createFile(userId, request.folderId(), fileName, sha, request.size(),
                        MimeTypeUtil.guess(fileName), userId);
                return new InitUploadResponse(null, 0, 0, List.of(), true, file.getId(), file.getCurrentVersion());
            }
        }

        int chunkSize = request.chunkSize() == null ? (int) properties.getChunkSize() : request.chunkSize();
        if (chunkSize < 1024 * 1024 || chunkSize > 64 * 1024 * 1024) {
            throw new BizException(ErrorCode.INVALID_PARAM, "分片大小必须在 1MiB - 64MiB 之间");
        }
        long size = request.size();
        int totalChunks = (int) Math.max(1, (size + chunkSize - 1) / chunkSize);

        Instant now = Instant.now();
        UploadSession session = new UploadSession();
        session.setId(UUID.randomUUID().toString().replace("-", ""));
        session.setUserId(userId);
        session.setFolderId(request.folderId());
        session.setTargetFileId(request.fileId());
        session.setFileName(fileName);
        session.setTotalSize(size);
        session.setSha256(sha);
        session.setChunkSize(chunkSize);
        session.setTotalChunks(totalChunks);
        session.setStatus(UploadSession.STATUS_UPLOADING);
        session.setCreatedAt(now);
        session.setExpiresAt(now.plus(properties.getUpload().getSessionTtl()));
        sessionRepository.save(session);

        try {
            Files.createDirectories(storagePaths.chunkDir(session.getId()));
        } catch (IOException e) {
            throw new BizException(ErrorCode.STORAGE_ERROR, "创建分片目录失败");
        }
        return new InitUploadResponse(session.getId(), chunkSize, totalChunks, List.of(), false, null, null);
    }

    @Transactional(readOnly = true)
    public UploadStatusResponse status(long userId, String uploadId) {
        UploadSession session = requireSession(userId, uploadId);
        List<Integer> uploaded = chunkRepository.findByUploadIdOrderByChunkIndexAsc(uploadId)
                .stream().map(UploadChunk::getChunkIndex).toList();
        return new UploadStatusResponse(session.getId(), session.getStatus(), session.getChunkSize(),
                session.getTotalChunks(), uploaded, session.getExpiresAt());
    }

    @Transactional
    public ChunkResponse putChunk(long userId, String uploadId, int index, long contentLength, InputStream body) {
        UploadSession session = requireSession(userId, uploadId);
        if (!UploadSession.STATUS_UPLOADING.equals(session.getStatus())) {
            throw new BizException(ErrorCode.UPLOAD_NOT_FOUND, "上传会话已结束");
        }
        ensureNotExpired(session);
        if (index < 0 || index >= session.getTotalChunks()) {
            throw new BizException(ErrorCode.CHUNK_INVALID);
        }
        long expected = expectedChunkSize(session, index);
        if (contentLength >= 0 && contentLength != expected) {
            throw new BizException(ErrorCode.SIZE_MISMATCH,
                    "分片大小不匹配，期望 " + expected + " 实际 " + contentLength);
        }

        Path dir = storagePaths.chunkDir(uploadId);
        Path target = storagePaths.chunkPath(uploadId, index);
        Path temp = dir.resolve(index + ".part.tmp");
        MessageDigest digest = sha256();
        long written = 0;
        try {
            Files.createDirectories(dir);
            try (OutputStream out = Files.newOutputStream(temp)) {
                byte[] buffer = new byte[64 * 1024];
                int read;
                while ((read = body.read(buffer)) != -1) {
                    written += read;
                    if (written > expected) {
                        throw new BizException(ErrorCode.SIZE_MISMATCH, "分片内容超过预期大小");
                    }
                    out.write(buffer, 0, read);
                    digest.update(buffer, 0, read);
                }
            }
            if (written != expected) {
                Files.deleteIfExists(temp);
                throw new BizException(ErrorCode.SIZE_MISMATCH,
                        "分片大小不匹配，期望 " + expected + " 实际 " + written);
            }
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            try {
                Files.deleteIfExists(temp);
            } catch (IOException ignored) {
                // ignore
            }
            throw new BizException(ErrorCode.STORAGE_ERROR, "写入分片失败");
        }

        String chunkSha = Hashing.hex(digest.digest());
        UploadChunk chunk = chunkRepository.findByUploadIdAndChunkIndex(uploadId, index).orElseGet(UploadChunk::new);
        chunk.setUploadId(uploadId);
        chunk.setChunkIndex(index);
        chunk.setSize(written);
        chunk.setSha256(chunkSha);
        chunk.setCreatedAt(Instant.now());
        chunkRepository.save(chunk);
        int uploaded = (int) chunkRepository.countByUploadId(uploadId);
        return new ChunkResponse(index, written, chunkSha, uploaded);
    }

    @Transactional
    public CompleteUploadResponse complete(long userId, String uploadId) {
        UploadSession session = sessionRepository.lockById(uploadId)
                .orElseThrow(() -> new BizException(ErrorCode.UPLOAD_NOT_FOUND));
        if (!session.getUserId().equals(userId)) {
            throw new BizException(ErrorCode.UPLOAD_NOT_FOUND);
        }
        if (UploadSession.STATUS_COMPLETED.equals(session.getStatus())) {
            return idempotentResult(userId, session);
        }
        if (!UploadSession.STATUS_UPLOADING.equals(session.getStatus())) {
            throw new BizException(ErrorCode.UPLOAD_NOT_FOUND, "上传会话已失效");
        }
        ensureNotExpired(session);

        List<UploadChunk> chunks = new ArrayList<>(chunkRepository.findByUploadIdOrderByChunkIndexAsc(uploadId));
        chunks.sort(Comparator.comparingInt(UploadChunk::getChunkIndex));
        if (chunks.size() != session.getTotalChunks()) {
            throw new BizException(ErrorCode.CHUNK_MISSING);
        }

        Path temp = storagePaths.newTempFile("merge");
        MessageDigest digest = sha256();
        long total = 0;
        try {
            Files.createDirectories(temp.getParent());
            try (OutputStream out = Files.newOutputStream(temp)) {
                for (int i = 0; i < session.getTotalChunks(); i++) {
                    Path chunkPath = storagePaths.chunkPath(uploadId, i);
                    if (!Files.isRegularFile(chunkPath)) {
                        throw new BizException(ErrorCode.CHUNK_MISSING, "缺少分片 " + i);
                    }
                    try (InputStream in = Files.newInputStream(chunkPath)) {
                        byte[] buffer = new byte[64 * 1024];
                        int read;
                        while ((read = in.read(buffer)) != -1) {
                            out.write(buffer, 0, read);
                            digest.update(buffer, 0, read);
                            total += read;
                        }
                    }
                }
            }
            if (total != session.getTotalSize()) {
                throw new BizException(ErrorCode.SIZE_MISMATCH,
                        "合并后大小不匹配，期望 " + session.getTotalSize() + " 实际 " + total);
            }
            String sha = Hashing.hex(digest.digest());
            if (session.getSha256() != null && !session.getSha256().equalsIgnoreCase(sha)) {
                throw new BizException(ErrorCode.SIZE_MISMATCH, "文件哈希不匹配");
            }
            blobService.store(sha, total, temp);

            CloudFile file;
            int versionNo;
            if (session.getTargetFileId() != null) {
                FileVersion version = fileService.addVersion(userId, session.getTargetFileId(), sha, total,
                        MimeTypeUtil.guess(session.getFileName()), "upload");
                file = fileService.requireFile(userId, session.getTargetFileId());
                versionNo = version.getVersionNo();
            } else {
                file = fileService.createFile(userId, session.getFolderId(), session.getFileName(), sha, total,
                        MimeTypeUtil.guess(session.getFileName()), userId);
                versionNo = file.getCurrentVersion();
                session.setTargetFileId(file.getId());
            }

            session.setStatus(UploadSession.STATUS_COMPLETED);
            sessionRepository.save(session);
            cleanupChunks(uploadId);
            return new CompleteUploadResponse(file.getId(), versionNo, sha, total);
        } catch (IOException e) {
            throw new BizException(ErrorCode.STORAGE_ERROR, "合并分片失败");
        } finally {
            try {
                Files.deleteIfExists(temp);
            } catch (IOException ignored) {
                // ignore
            }
        }
    }

    @Transactional
    public void cancel(long userId, String uploadId) {
        UploadSession session = sessionRepository.findByIdAndUserId(uploadId, userId)
                .orElseThrow(() -> new BizException(ErrorCode.UPLOAD_NOT_FOUND));
        if (UploadSession.STATUS_COMPLETED.equals(session.getStatus())) {
            return;
        }
        session.setStatus(UploadSession.STATUS_CANCELED);
        sessionRepository.save(session);
        cleanupChunks(uploadId);
    }

    @Transactional
    public int cleanupExpired() {
        Instant now = Instant.now();
        List<UploadSession> expired = sessionRepository.findByStatusAndExpiresAtBefore(UploadSession.STATUS_UPLOADING, now);
        int count = 0;
        for (UploadSession session : expired) {
            session.setStatus(UploadSession.STATUS_EXPIRED);
            sessionRepository.save(session);
            cleanupChunks(session.getId());
            count++;
        }
        return count;
    }

    public int cleanupOrphanChunkDirs() {
        int deleted = 0;
        Path root = storagePaths.chunksDir();
        if (!Files.isDirectory(root)) {
            return 0;
        }
        try (var children = Files.list(root)) {
            for (Path dir : children.toList()) {
                String name = dir.getFileName().toString();
                if (!sessionRepository.existsById(name)) {
                    deleteRecursively(dir);
                    deleted++;
                }
            }
        } catch (IOException e) {
            log.warn("扫描孤儿分片目录失败", e);
        }
        return deleted;
    }

    private CompleteUploadResponse idempotentResult(long userId, UploadSession session) {
        CloudFile file = fileService.requireFile(userId, session.getTargetFileId());
        return new CompleteUploadResponse(file.getId(), file.getCurrentVersion(), file.getSha256(), file.getSize());
    }

    private UploadSession requireSession(long userId, String uploadId) {
        return sessionRepository.findByIdAndUserId(uploadId, userId)
                .orElseThrow(() -> new BizException(ErrorCode.UPLOAD_NOT_FOUND));
    }

    private void ensureNotExpired(UploadSession session) {
        if (session.getExpiresAt().isBefore(Instant.now())) {
            session.setStatus(UploadSession.STATUS_EXPIRED);
            sessionRepository.save(session);
            cleanupChunks(session.getId());
            throw new BizException(ErrorCode.UPLOAD_NOT_FOUND, "上传会话已过期");
        }
    }

    private long expectedChunkSize(UploadSession session, int index) {
        if (index < session.getTotalChunks() - 1) {
            return session.getChunkSize();
        }
        long expected = session.getTotalSize() - (long) session.getChunkSize() * (session.getTotalChunks() - 1);
        return Math.max(0, expected);
    }

    private void cleanupChunks(String uploadId) {
        try {
            chunkRepository.deleteByUploadId(uploadId);
        } catch (Exception e) {
            log.warn("删除分片记录失败 uploadId={}", uploadId, e);
        }
        deleteRecursively(storagePaths.chunkDir(uploadId));
    }

    private void deleteRecursively(Path path) {
        if (path == null || !Files.exists(path)) {
            return;
        }
        try (var walk = Files.walk(path)) {
            walk.sorted(Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException ignored) {
                    // ignore
                }
            });
        } catch (IOException e) {
            log.warn("删除目录失败: {}", path, e);
        }
    }

    private String sanitize(String name) {
        String clean = name == null ? "" : name.trim().replace("/", "_").replace("\\", "_");
        if (clean.isEmpty()) {
            clean = "unnamed";
        }
        return clean.length() > 255 ? clean.substring(0, 255) : clean;
    }

    private MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
