package com.greydrive.upload;

import com.greydrive.common.ApiResponse;
import com.greydrive.security.CurrentUser;
import com.greydrive.upload.UploadDtos.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

import java.io.InputStream;

@Tag(name = "分片上传", description = "初始化、补传分片、合并、取消")
@RestController
@RequestMapping("/api/v1/uploads")
public class UploadController {

    private final UploadService uploadService;

    public UploadController(UploadService uploadService) {
        this.uploadService = uploadService;
    }

    @Operation(summary = "初始化上传（含秒传判断）")
    @PostMapping
    public ApiResponse<InitUploadResponse> init(@Valid @RequestBody InitUploadRequest request) {
        return ApiResponse.ok(uploadService.init(CurrentUser.id(), request));
    }

    @Operation(summary = "查询上传状态与已传分片")
    @GetMapping("/{uploadId}")
    public ApiResponse<UploadStatusResponse> status(@PathVariable String uploadId) {
        return ApiResponse.ok(uploadService.status(CurrentUser.id(), uploadId));
    }

    @Operation(summary = "上传或补传单个分片")
    @PutMapping(value = "/{uploadId}/chunks/{index}", consumes = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    public ApiResponse<ChunkResponse> putChunk(@PathVariable String uploadId, @PathVariable int index,
                                               HttpServletRequest request, InputStream body) {
        return ApiResponse.ok(uploadService.putChunk(CurrentUser.id(), uploadId, index,
                request.getContentLengthLong(), body));
    }

    @Operation(summary = "合并分片（幂等）")
    @PostMapping("/{uploadId}/complete")
    public ApiResponse<CompleteUploadResponse> complete(@PathVariable String uploadId) {
        return ApiResponse.ok(uploadService.complete(CurrentUser.id(), uploadId));
    }

    @Operation(summary = "取消上传并清理分片")
    @DeleteMapping("/{uploadId}")
    public ApiResponse<Void> cancel(@PathVariable String uploadId) {
        uploadService.cancel(CurrentUser.id(), uploadId);
        return ApiResponse.ok();
    }
}
