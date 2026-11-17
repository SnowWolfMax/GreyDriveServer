package com.greydrive.share;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.greydrive.blob.BlobService;
import com.greydrive.common.*;
import com.greydrive.config.GreyDriveProperties;
import com.greydrive.file.CloudFile;
import com.greydrive.file.CloudFileRepository;
import com.greydrive.file.FileService;
import com.greydrive.folder.Folder;
import com.greydrive.folder.FolderRepository;
import com.greydrive.oplog.OperationLogService;
import com.greydrive.preview.PreviewService;
import com.greydrive.security.JwtService;
import com.greydrive.share.ShareDtos.*;
import com.greydrive.storage.StorageService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class ShareService {

    private static final String CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final ShareRepository shareRepository;
    private final CloudFileRepository fileRepository;
    private final FolderRepository folderRepository;
    private final FileService fileService;
    private final PreviewService previewService;
    private final StorageService storageService;
    private final JwtService jwtService;
    private final PasswordEncoder passwordEncoder;
    private final GreyDriveProperties properties;
    private final OperationLogService operationLogService;
    private final Cache<String, AtomicInteger> codeAttempts = Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofMinutes(5))
            .maximumSize(10_000)
            .build();

    public ShareService(ShareRepository shareRepository, CloudFileRepository fileRepository,
                        FolderRepository folderRepository, FileService fileService, PreviewService previewService,
                        StorageService storageService, JwtService jwtService, PasswordEncoder passwordEncoder,
                        GreyDriveProperties properties, OperationLogService operationLogService) {
        this.shareRepository = shareRepository;
        this.fileRepository = fileRepository;
        this.folderRepository = folderRepository;
        this.fileService = fileService;
        this.previewService = previewService;
        this.storageService = storageService;
        this.jwtService = jwtService;
        this.passwordEncoder = passwordEncoder;
        this.properties = properties;
        this.operationLogService = operationLogService;
    }

    @Transactional
    public CreateShareResponse create(long userId, CreateShareRequest request) {
        if ((request.fileId() == null) == (request.folderId() == null)) {
            throw new BizException(ErrorCode.INVALID_PARAM, "fileId 与 folderId 必须二选一");
        }
        String permission = normalizePermission(request.permission());
        if (request.fileId() != null) {
            fileService.requireFile(userId, request.fileId());
        } else {
            folderRepository.findByIdAndUserIdAndDeletedFalse(request.folderId(), userId)
                    .orElseThrow(() -> new BizException(ErrorCode.FOLDER_NOT_FOUND));
        }

        String code = null;
        if (request.code() != null && !request.code().isBlank()) {
            code = request.code().trim();
            if (code.length() < 4 || code.length() > 32) {
                throw new BizException(ErrorCode.INVALID_PARAM, "提取码长度必须为 4-32");
            }
        } else if (properties.getShare().isCodeRequired()) {
            code = randomCode(properties.getShare().getCodeLength());
        }
        String token = randomToken();
        Instant now = Instant.now();
        Share share = new Share();
        share.setToken(token);
        share.setUserId(userId);
        share.setFileId(request.fileId());
        share.setFolderId(request.folderId());
        share.setCodeHash(code == null ? null : passwordEncoder.encode(code));
        share.setPermission(permission);
        share.setStatus(Share.STATUS_ACTIVE);
        share.setExpiresAt(resolveExpiry(request.expiresInSeconds(), now));
        share.setDownloadCount(0);
        share.setCreatedAt(now);
        share = shareRepository.save(share);

        operationLogService.record(userId, "SHARE_CREATE", "SHARE", share.getId(), permission,
                WebRequestInfo.clientIp(), WebRequestInfo.userAgent(), true);
        return new CreateShareResponse(share.getId(), token, "/api/v1/public/shares/" + token, code,
                permission, share.getExpiresAt());
    }

    @Transactional(readOnly = true)
    public org.springframework.data.domain.Page<ShareView> list(long userId,
                                                                 org.springframework.data.domain.Pageable pageable) {
        return shareRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable)
                .map(s -> new ShareView(s.getId(), s.getToken(), s.getFileId(), s.getFolderId(), s.getPermission(),
                        s.getCodeHash() != null, s.getExpiresAt(), s.getStatus(), s.getDownloadCount(),
                        s.getCreatedAt()));
    }

    @Transactional
    public void cancel(long userId, long shareId) {
        Share share = shareRepository.findByIdAndUserId(shareId, userId)
                .orElseThrow(() -> new BizException(ErrorCode.SHARE_NOT_FOUND));
        share.setStatus(Share.STATUS_CANCELED);
        shareRepository.save(share);
        operationLogService.record(userId, "SHARE_CANCEL", "SHARE", shareId, null,
                WebRequestInfo.clientIp(), WebRequestInfo.userAgent(), true);
    }

    @Transactional(readOnly = true)
    public PublicShareInfo publicInfo(String token) {
        Share share = activeShare(token);
        if (share.getFileId() != null) {
            CloudFile file = fileRepository.findByIdAndUserIdAndDeletedFalse(share.getFileId(), share.getUserId())
                    .orElseThrow(() -> new BizException(ErrorCode.SHARE_NOT_FOUND));
            return new PublicShareInfo("FILE", file.getName(), file.getId(), null, share.getPermission(),
                    share.getCodeHash() != null, share.getExpiresAt());
        }
        Folder folder = folderRepository.findByIdAndUserIdAndDeletedFalse(share.getFolderId(), share.getUserId())
                .orElseThrow(() -> new BizException(ErrorCode.SHARE_NOT_FOUND));
        return new PublicShareInfo("FOLDER", folder.getName(), null, folder.getId(), share.getPermission(),
                share.getCodeHash() != null, share.getExpiresAt());
    }

    @Transactional(readOnly = true)
    public VerifyShareResponse verify(String token, String code) {
        Share share = activeShare(token);
        if (share.getCodeHash() != null) {
            String key = token + "|" + WebRequestInfo.clientIp();
            AtomicInteger attempts = codeAttempts.get(key, k -> new AtomicInteger());
            if (attempts.get() >= 5) {
                throw new BizException(ErrorCode.RATE_LIMITED, "提取码尝试次数过多，请稍后再试");
            }
            if (code == null || code.isBlank() || !passwordEncoder.matches(code, share.getCodeHash())) {
                attempts.incrementAndGet();
                throw new BizException(ErrorCode.SHARE_CODE_INVALID);
            }
            codeAttempts.invalidate(key);
        }
        String shareToken = jwtService.issueShareToken(share.getId(), share.getToken());
        return new VerifyShareResponse(shareToken, share.getPermission(),
                Instant.now().plus(properties.getSecurity().getShareAccessTtl()));
    }

    @Transactional(readOnly = true)
    public PageResult<ShareFileItem> listFiles(String token, String accessToken, Long parentId,
                                               int page, int size) {
        Share share = validateAccess(token, accessToken);
        if (share.getFolderId() == null) {
            throw new BizException(ErrorCode.SHARE_PERMISSION_DENIED, "该分享不是目录分享");
        }
        Long rootId = share.getFolderId();
        Long target = parentId == null ? rootId : parentId;
        if (!isFolderInSubtree(target, rootId, share.getUserId())) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }
        List<ShareFileItem> items = new ArrayList<>();
        for (Folder folder : folderRepository.findByUserIdAndParentIdAndDeletedFalse(share.getUserId(), target)) {
            items.add(new ShareFileItem(folder.getId(), "FOLDER", folder.getName(), 0, null, folder.getUpdatedAt()));
        }
        for (CloudFile file : fileRepository.findByUserIdAndFolderIdAndDeletedFalse(share.getUserId(), target)) {
            items.add(new ShareFileItem(file.getId(), "FILE", file.getName(), file.getSize(), file.getMimeType(),
                    file.getUpdatedAt()));
        }
        items.sort(Comparator.comparing(ShareFileItem::name, String.CASE_INSENSITIVE_ORDER));

        int pageSize = Math.max(1, Math.min(size, properties.getPagination().getMaxSize()));
        int pageNo = Math.max(1, page);
        long total = items.size();
        int from = Math.min((pageNo - 1) * pageSize, items.size());
        int to = Math.min(from + pageSize, items.size());
        return PageResult.of(new ArrayList<>(items.subList(from, to)), pageNo, pageSize, total);
    }

    @Transactional
    public ShareDownload download(String token, String accessToken, Long fileId, String rangeHeader) {
        Share share = validateAccess(token, accessToken);
        if (!Share.PERM_DOWNLOAD.equals(share.getPermission())) {
            throw new BizException(ErrorCode.SHARE_PERMISSION_DENIED, "该分享仅支持预览");
        }
        CloudFile file = resolveSharedFile(share, fileId);
        if (!Files.isRegularFile(storageService.path(BlobService.storageKey(file.getSha256())))) {
            throw new BizException(ErrorCode.STORAGE_ERROR, "文件内容缺失");
        }
        share.setDownloadCount(share.getDownloadCount() + 1);
        shareRepository.save(share);
        operationLogService.record(share.getUserId(), "SHARE_DOWNLOAD", "SHARE", share.getId(), file.getName(),
                WebRequestInfo.clientIp(), WebRequestInfo.userAgent(), true);
        return new ShareDownload(storageService.path(BlobService.storageKey(file.getSha256())),
                file.getName(), file.getMimeType());
    }

    @Transactional(readOnly = true)
    public Path thumbnail(String token, String accessToken, Long fileId, int size) {
        Share share = validateAccess(token, accessToken);
        CloudFile file = resolveSharedFile(share, fileId);
        return previewService.thumbnail(file, size);
    }

    private CloudFile resolveSharedFile(Share share, Long fileId) {
        Long targetId = share.getFileId() != null ? share.getFileId() : fileId;
        if (targetId == null) {
            throw new BizException(ErrorCode.INVALID_PARAM, "缺少 fileId");
        }
        if (share.getFileId() != null && !share.getFileId().equals(targetId)) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }
        CloudFile file = fileRepository.findByIdAndUserIdAndDeletedFalse(targetId, share.getUserId())
                .orElseThrow(() -> new BizException(ErrorCode.NOT_FOUND));
        if (share.getFolderId() != null
                && !isFolderInSubtree(file.getFolderId(), share.getFolderId(), share.getUserId())) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }
        return file;
    }

    private Share validateAccess(String token, String accessToken) {
        Share share = activeShare(token);
        if (share.getCodeHash() == null && (accessToken == null || accessToken.isBlank())) {
            return share;
        }
        if (accessToken == null || accessToken.isBlank()) {
            throw new BizException(ErrorCode.SHARE_CODE_INVALID, "缺少分享访问令牌");
        }
        try {
            JwtService.SharePrincipal principal = jwtService.parseShareToken(accessToken);
            if (!principal.shareId().equals(share.getId()) || !share.getToken().equals(principal.shareToken())) {
                throw new BizException(ErrorCode.SHARE_CODE_INVALID, "分享访问令牌不匹配");
            }
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw new BizException(ErrorCode.SHARE_CODE_INVALID, "分享访问令牌无效");
        }
        return share;
    }

    private Share activeShare(String token) {
        Share share = shareRepository.findByTokenAndStatus(token, Share.STATUS_ACTIVE)
                .orElseThrow(() -> new BizException(ErrorCode.SHARE_NOT_FOUND));
        if (share.getExpiresAt() != null && share.getExpiresAt().isBefore(Instant.now())) {
            throw new BizException(ErrorCode.SHARE_EXPIRED);
        }
        return share;
    }

    private boolean isFolderInSubtree(Long folderId, Long rootId, Long userId) {
        Long cursor = folderId;
        int guard = 0;
        while (cursor != null && guard++ < 10_000) {
            if (cursor.equals(rootId)) {
                return true;
            }
            Folder folder = folderRepository.findByIdAndUserId(cursor, userId).orElse(null);
            cursor = folder == null ? null : folder.getParentId();
        }
        return false;
    }

    private String normalizePermission(String permission) {
        if (permission == null || permission.isBlank()) {
            return Share.PERM_DOWNLOAD;
        }
        String value = permission.toUpperCase(Locale.ROOT);
        if (!Share.PERM_VIEW.equals(value) && !Share.PERM_DOWNLOAD.equals(value)) {
            throw new BizException(ErrorCode.INVALID_PARAM, "permission 只能是 VIEW 或 DOWNLOAD");
        }
        return value;
    }

    private Instant resolveExpiry(Long expiresInSeconds, Instant now) {
        if (expiresInSeconds != null && expiresInSeconds > 0) {
            return now.plusSeconds(expiresInSeconds);
        }
        Duration defaultTtl = properties.getShare().getDefaultTtl();
        return defaultTtl == null ? null : now.plus(defaultTtl);
    }

    private String randomToken() {
        byte[] bytes = new byte[16];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String randomCode(int length) {
        int len = Math.max(4, Math.min(length, 12));
        StringBuilder sb = new StringBuilder(len);
        for (int i = 0; i < len; i++) {
            sb.append(CODE_ALPHABET.charAt(RANDOM.nextInt(CODE_ALPHABET.length())));
        }
        return sb.toString();
    }
}
