package com.greydrive.share;

import com.greydrive.common.ApiResponse;
import com.greydrive.common.DownloadResponses;
import com.greydrive.common.PageResult;
import com.greydrive.share.ShareDtos.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

@Tag(name = "分享访问", description = "公开的分享元信息、提取码校验与下载")
@RestController
@RequestMapping("/api/v1/public/shares")
public class PublicShareController {

    private static final String SHARE_TOKEN_HEADER = "X-Share-Token";

    private final ShareService shareService;

    public PublicShareController(ShareService shareService) {
        this.shareService = shareService;
    }

    @Operation(summary = "分享元信息")
    @GetMapping("/{token}")
    public ApiResponse<PublicShareInfo> info(@PathVariable String token) {
        return ApiResponse.ok(shareService.publicInfo(token));
    }

    @Operation(summary = "校验提取码并换取分享访问令牌")
    @PostMapping("/{token}/verify")
    public ApiResponse<VerifyShareResponse> verify(@PathVariable String token,
                                                   @RequestBody(required = false) VerifyShareRequest request) {
        return ApiResponse.ok(shareService.verify(token, request == null ? null : request.code()));
    }

    @Operation(summary = "目录分享的文件列表")
    @GetMapping("/{token}/files")
    public ApiResponse<PageResult<ShareFileItem>> files(
            @PathVariable String token,
            @RequestHeader(value = SHARE_TOKEN_HEADER, required = false) String accessToken,
            @RequestParam(required = false) Long parentId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "50") int size) {
        return ApiResponse.ok(shareService.listFiles(token, accessToken, parentId, page, size));
    }

    @Operation(summary = "分享文件下载（支持 Range）")
    @GetMapping("/{token}/files/{fileId}/download")
    public ResponseEntity<?> download(@PathVariable String token, @PathVariable long fileId,
                                      @RequestHeader(value = SHARE_TOKEN_HEADER, required = false) String accessToken,
                                      @RequestHeader(value = HttpHeaders.RANGE, required = false) String range)
            throws IOException {
        ShareDownload content = shareService.download(token, accessToken, fileId, range);
        return DownloadResponses.build(content.path(), content.mimeType(), content.fileName(), range);
    }

    @Operation(summary = "分享图片缩略图")
    @GetMapping("/{token}/thumbnail")
    public ResponseEntity<Resource> thumbnail(
            @PathVariable String token,
            @RequestHeader(value = SHARE_TOKEN_HEADER, required = false) String accessToken,
            @RequestParam(required = false) Long fileId,
            @RequestParam(defaultValue = "256") int size) {
        Path path = shareService.thumbnail(token, accessToken, fileId, size);
        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_JPEG)
                .cacheControl(CacheControl.maxAge(1, TimeUnit.HOURS))
                .body(new FileSystemResource(path));
    }
}
