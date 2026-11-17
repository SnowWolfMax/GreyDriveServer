package com.greydrive.recent;

import com.greydrive.common.ApiResponse;
import com.greydrive.common.PageResult;
import com.greydrive.common.Paging;
import com.greydrive.config.GreyDriveProperties;
import com.greydrive.recent.RecentService.RecentView;
import com.greydrive.security.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.*;

import java.util.Set;

@Tag(name = "最近访问", description = "最近访问记录")
@RestController
@RequestMapping("/api/v1/recent")
public class RecentController {

    private static final Set<String> SORT_FIELDS = Set.of("accessedAt");

    private final RecentService recentService;
    private final GreyDriveProperties properties;

    public RecentController(RecentService recentService, GreyDriveProperties properties) {
        this.recentService = recentService;
        this.properties = properties;
    }

    @Operation(summary = "最近访问列表")
    @GetMapping
    public ApiResponse<PageResult<RecentView>> list(@RequestParam(defaultValue = "1") int page,
                                                    @RequestParam(required = false) Integer size,
                                                    @RequestParam(required = false) String sort) {
        Pageable pageable = Paging.of(page, size, sort, SORT_FIELDS, "accessedAt,desc",
                properties.getPagination().getDefaultSize(), properties.getPagination().getMaxSize());
        return ApiResponse.ok(PageResult.of(recentService.list(CurrentUser.id(), pageable)));
    }

    @Operation(summary = "清空最近访问")
    @DeleteMapping
    public ApiResponse<Void> clear() {
        recentService.clear(CurrentUser.id());
        return ApiResponse.ok();
    }
}
