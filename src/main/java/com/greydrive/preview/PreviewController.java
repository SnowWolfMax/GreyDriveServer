package com.greydrive.preview;

import com.greydrive.common.ApiResponse;
import com.greydrive.file.CloudFile;
import com.greydrive.file.FileService;
import com.greydrive.preview.PreviewService.TextPreview;
import com.greydrive.security.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

@Tag(name = "预览", description = "图片缩略图与文本预览")
@RestController
@RequestMapping("/api/v1/files/{id}")
public class PreviewController {

    private final FileService fileService;
    private final PreviewService previewService;

    public PreviewController(FileService fileService, PreviewService previewService) {
        this.fileService = fileService;
        this.previewService = previewService;
    }

    @Operation(summary = "图片缩略图")
    @GetMapping("/thumbnail")
    public ResponseEntity<Resource> thumbnail(@PathVariable long id,
                                              @RequestParam(defaultValue = "256") int size) {
        CloudFile file = fileService.requireFile(CurrentUser.id(), id);
        Path path = previewService.thumbnail(file, size);
        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_JPEG)
                .cacheControl(CacheControl.maxAge(1, TimeUnit.HOURS))
                .body(new FileSystemResource(path));
    }

    @Operation(summary = "文本预览")
    @GetMapping("/preview/text")
    public ApiResponse<TextPreview> text(@PathVariable long id) {
        CloudFile file = fileService.requireFile(CurrentUser.id(), id);
        return ApiResponse.ok(previewService.text(file));
    }
}
