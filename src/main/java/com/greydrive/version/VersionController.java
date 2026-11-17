package com.greydrive.version;

import com.greydrive.common.*;
import com.greydrive.security.CurrentUser;
import com.greydrive.version.VersionDtos.VersionView;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.nio.file.Files;
import java.util.List;

@Tag(name = "版本历史", description = "版本列表、下载、回滚")
@RestController
@RequestMapping("/api/v1/files/{fileId}/versions")
public class VersionController {

    private final VersionService versionService;

    public VersionController(VersionService versionService) {
        this.versionService = versionService;
    }

    @Operation(summary = "版本列表")
    @GetMapping
    public ApiResponse<List<VersionView>> list(@PathVariable long fileId) {
        return ApiResponse.ok(versionService.list(CurrentUser.id(), fileId));
    }

    @Operation(summary = "下载指定版本（支持 Range）")
    @GetMapping("/{versionNo}/download")
    public ResponseEntity<?> download(@PathVariable long fileId, @PathVariable int versionNo,
                                      @RequestHeader(value = HttpHeaders.RANGE, required = false) String range)
            throws IOException {
        VersionService.VersionContent content = versionService.content(CurrentUser.id(), fileId, versionNo);
        if (!Files.isRegularFile(content.path())) {
            throw new BizException(ErrorCode.STORAGE_ERROR, "版本内容缺失");
        }
        return DownloadResponses.build(content.path(), content.mimeType(), content.fileName(), range);
    }

    @Operation(summary = "回滚到指定版本（生成新版本）")
    @PostMapping("/{versionNo}/rollback")
    public ApiResponse<VersionView> rollback(@PathVariable long fileId, @PathVariable int versionNo) {
        return ApiResponse.ok(versionService.rollback(CurrentUser.id(), fileId, versionNo));
    }
}
