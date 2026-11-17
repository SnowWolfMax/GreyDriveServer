package com.greydrive.oplog;

import com.greydrive.common.ApiResponse;
import com.greydrive.common.PageResult;
import com.greydrive.common.Paging;
import com.greydrive.config.GreyDriveProperties;
import com.greydrive.security.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Set;

@Tag(name = "操作日志", description = "当前用户的操作日志")
@RestController
@RequestMapping("/api/v1/logs")
public class OperationLogController {

    private static final Set<String> SORT_FIELDS = Set.of("createdAt");

    private final OperationLogService operationLogService;
    private final GreyDriveProperties properties;

    public OperationLogController(OperationLogService operationLogService, GreyDriveProperties properties) {
        this.operationLogService = operationLogService;
        this.properties = properties;
    }

    @Operation(summary = "查询操作日志")
    @GetMapping
    public ApiResponse<PageResult<OperationLog>> list(
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String resourceType,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String sort) {
        Pageable pageable = Paging.of(page, size, sort, SORT_FIELDS, "createdAt,desc",
                properties.getPagination().getDefaultSize(), properties.getPagination().getMaxSize());
        return ApiResponse.ok(PageResult.of(
                operationLogService.search(CurrentUser.id(), action, resourceType, from, to, pageable)));
    }
}
