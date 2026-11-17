package com.greydrive.folder;

import com.greydrive.common.ApiResponse;
import com.greydrive.common.PageResult;
import com.greydrive.common.Paging;
import com.greydrive.config.GreyDriveProperties;
import com.greydrive.folder.FolderDtos.Breadcrumb;
import com.greydrive.folder.FolderDtos.CreateFolderRequest;
import com.greydrive.folder.FolderDtos.FolderView;
import com.greydrive.folder.FolderDtos.MoveFolderRequest;
import com.greydrive.folder.FolderDtos.RenameFolderRequest;
import com.greydrive.security.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.*;

import java.util.Set;

@Tag(name = "目录", description = "目录增删改查与移动")
@RestController
@RequestMapping("/api/v1/folders")
public class FolderController {

    private static final Set<String> SORT_FIELDS = Set.of("name", "createdAt", "updatedAt");

    private final FolderService folderService;
    private final GreyDriveProperties properties;

    public FolderController(FolderService folderService, GreyDriveProperties properties) {
        this.folderService = folderService;
        this.properties = properties;
    }

    @Operation(summary = "新建目录")
    @PostMapping
    public ApiResponse<FolderView> create(@Valid @RequestBody CreateFolderRequest request) {
        return ApiResponse.ok(folderService.create(CurrentUser.id(), request.parentId(), request.name()));
    }

    @Operation(summary = "列出子目录")
    @GetMapping
    public ApiResponse<PageResult<FolderView>> list(
            @Parameter(description = "父目录 id，省略表示根目录") @RequestParam(required = false) Long parentId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String sort) {
        Pageable pageable = Paging.of(page, size, sort, SORT_FIELDS, "name,asc",
                properties.getPagination().getDefaultSize(), properties.getPagination().getMaxSize());
        return ApiResponse.ok(PageResult.of(folderService.list(CurrentUser.id(), parentId, pageable)));
    }

    @Operation(summary = "目录详情")
    @GetMapping("/{id}")
    public ApiResponse<FolderView> get(@PathVariable long id) {
        return ApiResponse.ok(folderService.get(CurrentUser.id(), id));
    }

    @Operation(summary = "目录面包屑")
    @GetMapping("/{id}/path")
    public ApiResponse<Breadcrumb> path(@PathVariable long id) {
        return ApiResponse.ok(folderService.path(CurrentUser.id(), id));
    }

    @Operation(summary = "重命名目录")
    @PatchMapping("/{id}")
    public ApiResponse<FolderView> rename(@PathVariable long id, @Valid @RequestBody RenameFolderRequest request) {
        return ApiResponse.ok(folderService.rename(CurrentUser.id(), id, request.name()));
    }

    @Operation(summary = "移动目录")
    @PostMapping("/{id}/move")
    public ApiResponse<FolderView> move(@PathVariable long id, @RequestBody MoveFolderRequest request) {
        return ApiResponse.ok(folderService.move(CurrentUser.id(), id, request.parentId()));
    }

    @Operation(summary = "删除目录（软删除进回收站）")
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable long id) {
        folderService.delete(CurrentUser.id(), id);
        return ApiResponse.ok();
    }
}
