package com.greydrive.file;

import com.greydrive.blob.BlobService;
import com.greydrive.common.*;
import com.greydrive.config.GreyDriveProperties;
import com.greydrive.file.FileDtos.CheckHashRequest;
import com.greydrive.file.FileDtos.CheckHashResponse;
import com.greydrive.file.FileDtos.FileView;
import com.greydrive.file.FileDtos.MoveFileRequest;
import com.greydrive.file.FileDtos.RenameFileRequest;
import com.greydrive.oplog.OperationLogService;
import com.greydrive.recent.RecentService;
import com.greydrive.security.CurrentUser;
import com.greydrive.storage.StorageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Set;

@Tag(name = "文件", description = "文件列表、筛选、秒传、下载")
@RestController
@RequestMapping("/api/v1")
public class FileController {

    private static final Set<String> SORT_FIELDS = Set.of("name", "size", "createdAt", "updatedAt", "lastAccessedAt");

    private final FileService fileService;
    private final RecentService recentService;
    private final StorageService storageService;
    private final OperationLogService operationLogService;
    private final GreyDriveProperties properties;

    public FileController(FileService fileService, RecentService recentService, StorageService storageService,
                          OperationLogService operationLogService, GreyDriveProperties properties) {
        this.fileService = fileService;
        this.recentService = recentService;
        this.storageService = storageService;
        this.operationLogService = operationLogService;
        this.properties = properties;
    }

    @Operation(summary = "文件列表与筛选")
    @GetMapping("/files")
    public ApiResponse<PageResult<FileView>> search(
            @Parameter(description = "目录 id；0 表示根目录，省略表示全部") @RequestParam(required = false) Long folderId,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String mimeType,
            @RequestParam(required = false) String ext,
            @RequestParam(required = false) Long minSize,
            @RequestParam(required = false) Long maxSize,
            @RequestParam(required = false) Long tagId,
            @RequestParam(required = false) Boolean favorite,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String sort) {
        Pageable pageable = Paging.of(page, size, sort, SORT_FIELDS, "updatedAt,desc",
                properties.getPagination().getDefaultSize(), properties.getPagination().getMaxSize());
        return ApiResponse.ok(PageResult.of(fileService.search(CurrentUser.id(), folderId, keyword, mimeType, ext,
                minSize, maxSize, tagId, favorite, from, to, pageable)));
    }

    @Operation(summary = "文件详情")
    @GetMapping("/files/{id}")
    public ApiResponse<FileView> get(@PathVariable long id) {
        return ApiResponse.ok(fileService.get(CurrentUser.id(), id));
    }

    @Operation(summary = "重命名文件")
    @PatchMapping("/files/{id}")
    public ApiResponse<FileView> rename(@PathVariable long id, @Valid @RequestBody RenameFileRequest request) {
        return ApiResponse.ok(fileService.rename(CurrentUser.id(), id, request.name()));
    }

    @Operation(summary = "移动文件")
    @PostMapping("/files/{id}/move")
    public ApiResponse<FileView> move(@PathVariable long id, @RequestBody MoveFileRequest request) {
        return ApiResponse.ok(fileService.move(CurrentUser.id(), id, request.folderId()));
    }

    @Operation(summary = "删除文件（软删除进回收站）")
    @DeleteMapping("/files/{id}")
    public ApiResponse<Void> delete(@PathVariable long id) {
        fileService.softDelete(CurrentUser.id(), id);
        return ApiResponse.ok();
    }

    @Operation(summary = "按哈希秒传")
    @PostMapping("/files/check-hash")
    public ApiResponse<CheckHashResponse> checkHash(@Valid @RequestBody CheckHashRequest request) {
        return ApiResponse.ok(fileService.instantUpload(CurrentUser.id(), request.sha256(), request.size(),
                request.fileName(), request.folderId()));
    }

    @Operation(summary = "下载文件（支持 HTTP Range）")
    @GetMapping("/files/{id}/download")
    public ResponseEntity<?> download(@PathVariable long id,
                                      @RequestHeader(value = HttpHeaders.RANGE, required = false) String range)
            throws IOException {
        long userId = CurrentUser.id();
        CloudFile file = fileService.requireFile(userId, id);
        if (file.getSha256() == null) {
            throw new BizException(ErrorCode.FILE_NOT_FOUND);
        }
        Path path = storageService.path(BlobService.storageKey(file.getSha256()));
        if (!Files.isRegularFile(path)) {
            throw new BizException(ErrorCode.STORAGE_ERROR, "文件内容缺失");
        }
        recentService.touch(userId, id);
        operationLogService.record(userId, "FILE_DOWNLOAD", "FILE", id, file.getName(),
                WebRequestInfo.clientIp(), WebRequestInfo.userAgent(), true);
        return DownloadResponses.build(path, file.getMimeType(), file.getName(), range);
    }

    @Operation(summary = "收藏文件")
    @PutMapping("/files/{id}/favorite")
    public ApiResponse<FileView> favorite(@PathVariable long id) {
        return ApiResponse.ok(fileService.setFavorite(CurrentUser.id(), id, true));
    }

    @Operation(summary = "取消收藏")
    @DeleteMapping("/files/{id}/favorite")
    public ApiResponse<FileView> unfavorite(@PathVariable long id) {
        return ApiResponse.ok(fileService.setFavorite(CurrentUser.id(), id, false));
    }

    @Operation(summary = "收藏列表")
    @GetMapping("/favorites")
    public ApiResponse<PageResult<FileView>> favorites(@RequestParam(defaultValue = "1") int page,
                                                       @RequestParam(required = false) Integer size,
                                                       @RequestParam(required = false) String sort) {
        Pageable pageable = Paging.of(page, size, sort, SORT_FIELDS, "updatedAt,desc",
                properties.getPagination().getDefaultSize(), properties.getPagination().getMaxSize());
        return ApiResponse.ok(PageResult.of(fileService.favorites(CurrentUser.id(), pageable)));
    }
}
