package com.greydrive.tag;

import com.greydrive.common.ApiResponse;
import com.greydrive.security.CurrentUser;
import com.greydrive.tag.TagDtos.CreateTagRequest;
import com.greydrive.tag.TagDtos.SetFileTagsRequest;
import com.greydrive.tag.TagDtos.TagView;
import com.greydrive.tag.TagDtos.UpdateTagRequest;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@io.swagger.v3.oas.annotations.tags.Tag(name = "标签", description = "标签管理与文件打标")
@RestController
@RequestMapping("/api/v1")
public class TagController {

    private final TagService tagService;

    public TagController(TagService tagService) {
        this.tagService = tagService;
    }

    @Operation(summary = "新建标签")
    @PostMapping("/tags")
    public ApiResponse<TagView> create(@Valid @RequestBody CreateTagRequest request) {
        return ApiResponse.ok(tagService.create(CurrentUser.id(), request));
    }

    @Operation(summary = "我的标签")
    @GetMapping("/tags")
    public ApiResponse<List<TagView>> list() {
        return ApiResponse.ok(tagService.list(CurrentUser.id()));
    }

    @Operation(summary = "更新标签")
    @PatchMapping("/tags/{id}")
    public ApiResponse<TagView> update(@PathVariable long id, @Valid @RequestBody UpdateTagRequest request) {
        return ApiResponse.ok(tagService.update(CurrentUser.id(), id, request));
    }

    @Operation(summary = "删除标签")
    @DeleteMapping("/tags/{id}")
    public ApiResponse<Void> delete(@PathVariable long id) {
        tagService.delete(CurrentUser.id(), id);
        return ApiResponse.ok();
    }

    @Operation(summary = "覆盖设置文件标签")
    @PutMapping("/files/{id}/tags")
    public ApiResponse<List<TagView>> setFileTags(@PathVariable long id,
                                                  @Valid @RequestBody SetFileTagsRequest request) {
        return ApiResponse.ok(tagService.setFileTags(CurrentUser.id(), id, request.tagIds()));
    }

    @Operation(summary = "给文件添加标签")
    @PostMapping("/files/{id}/tags/{tagId}")
    public ApiResponse<List<TagView>> addTag(@PathVariable long id, @PathVariable long tagId) {
        return ApiResponse.ok(tagService.addFileTag(CurrentUser.id(), id, tagId));
    }

    @Operation(summary = "移除文件标签")
    @DeleteMapping("/files/{id}/tags/{tagId}")
    public ApiResponse<List<TagView>> removeTag(@PathVariable long id, @PathVariable long tagId) {
        return ApiResponse.ok(tagService.removeFileTag(CurrentUser.id(), id, tagId));
    }

    @Operation(summary = "文件标签列表")
    @GetMapping("/files/{id}/tags")
    public ApiResponse<List<TagView>> fileTags(@PathVariable long id) {
        return ApiResponse.ok(tagService.fileTags(CurrentUser.id(), id));
    }
}
