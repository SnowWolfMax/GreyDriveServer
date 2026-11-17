package com.greydrive.share;

import com.greydrive.common.ApiResponse;
import com.greydrive.common.PageResult;
import com.greydrive.common.Paging;
import com.greydrive.config.GreyDriveProperties;
import com.greydrive.security.CurrentUser;
import com.greydrive.share.ShareDtos.CreateShareRequest;
import com.greydrive.share.ShareDtos.CreateShareResponse;
import com.greydrive.share.ShareDtos.ShareView;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.*;

import java.util.Set;

@Tag(name = "分享", description = "创建、查看、取消分享")
@RestController
@RequestMapping("/api/v1/shares")
public class ShareController {

    private static final Set<String> SORT_FIELDS = Set.of("createdAt");

    private final ShareService shareService;
    private final GreyDriveProperties properties;

    public ShareController(ShareService shareService, GreyDriveProperties properties) {
        this.shareService = shareService;
        this.properties = properties;
    }

    @Operation(summary = "创建分享（返回一次性明文提取码）")
    @PostMapping
    public ApiResponse<CreateShareResponse> create(@Valid @RequestBody CreateShareRequest request) {
        return ApiResponse.ok(shareService.create(CurrentUser.id(), request));
    }

    @Operation(summary = "我的分享列表")
    @GetMapping
    public ApiResponse<PageResult<ShareView>> list(@RequestParam(defaultValue = "1") int page,
                                                   @RequestParam(required = false) Integer size,
                                                   @RequestParam(required = false) String sort) {
        Pageable pageable = Paging.of(page, size, sort, SORT_FIELDS, "createdAt,desc",
                properties.getPagination().getDefaultSize(), properties.getPagination().getMaxSize());
        return ApiResponse.ok(PageResult.of(shareService.list(CurrentUser.id(), pageable)));
    }

    @Operation(summary = "取消分享")
    @DeleteMapping("/{id}")
    public ApiResponse<Void> cancel(@PathVariable long id) {
        shareService.cancel(CurrentUser.id(), id);
        return ApiResponse.ok();
    }
}
