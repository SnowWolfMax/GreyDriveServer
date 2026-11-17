package com.greydrive.recycle;

import com.greydrive.common.ApiResponse;
import com.greydrive.common.PageResult;
import com.greydrive.common.Paging;
import com.greydrive.common.WebRequestInfo;
import com.greydrive.config.GreyDriveProperties;
import com.greydrive.oplog.OperationLogService;
import com.greydrive.recycle.RecycleDtos.RecycleItem;
import com.greydrive.recycle.RecycleDtos.RestoreRequest;
import com.greydrive.security.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.*;

import java.util.Set;

@Tag(name = "回收站", description = "软删除列表、恢复、彻底删除、清空")
@RestController
@RequestMapping("/api/v1/recycle")
public class RecycleController {

    private static final Set<String> SORT_FIELDS = Set.of("deletedAt", "name", "size");

    private final RecycleService recycleService;
    private final OperationLogService operationLogService;
    private final GreyDriveProperties properties;

    public RecycleController(RecycleService recycleService, OperationLogService operationLogService,
                             GreyDriveProperties properties) {
        this.recycleService = recycleService;
        this.operationLogService = operationLogService;
        this.properties = properties;
    }

    @Operation(summary = "回收站列表")
    @GetMapping
    public ApiResponse<PageResult<RecycleItem>> list(
            @Parameter(description = "FILE 或 FOLDER") @RequestParam(defaultValue = "FILE") String type,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String sort) {
        Pageable pageable = Paging.of(page, size, sort, SORT_FIELDS, "deletedAt,desc",
                properties.getPagination().getDefaultSize(), properties.getPagination().getMaxSize());
        return ApiResponse.ok(PageResult.of(recycleService.list(CurrentUser.id(), type, pageable)));
    }

    @Operation(summary = "批量恢复")
    @PostMapping("/restore")
    public ApiResponse<Integer> restore(@Valid @RequestBody RestoreRequest request) {
        long userId = CurrentUser.id();
        int restored = recycleService.restore(userId, request.fileIds(), request.folderIds());
        operationLogService.record(userId, "RECYCLE_RESTORE", null, null, "restored=" + restored,
                WebRequestInfo.clientIp(), WebRequestInfo.userAgent(), true);
        return ApiResponse.ok(restored);
    }

    @Operation(summary = "彻底删除")
    @DeleteMapping("/{id}")
    public ApiResponse<Void> purge(@PathVariable long id,
                                   @Parameter(description = "FILE 或 FOLDER") @RequestParam(defaultValue = "FILE") String type) {
        long userId = CurrentUser.id();
        recycleService.purge(userId, type, id);
        operationLogService.record(userId, "RECYCLE_PURGE", type.toUpperCase(), id, null,
                WebRequestInfo.clientIp(), WebRequestInfo.userAgent(), true);
        return ApiResponse.ok();
    }

    @Operation(summary = "清空回收站")
    @DeleteMapping
    public ApiResponse<Integer> empty() {
        long userId = CurrentUser.id();
        int count = recycleService.empty(userId);
        operationLogService.record(userId, "RECYCLE_EMPTY", null, null, "purged=" + count,
                WebRequestInfo.clientIp(), WebRequestInfo.userAgent(), true);
        return ApiResponse.ok(count);
    }
}
