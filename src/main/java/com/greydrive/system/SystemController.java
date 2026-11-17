package com.greydrive.system;

import com.greydrive.common.ApiResponse;
import com.greydrive.system.SystemDtos.HealthView;
import com.greydrive.system.SystemDtos.StorageStatsView;
import com.greydrive.system.SystemDtos.SystemInfoView;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "系统", description = "健康检查、系统信息、存储统计")
@RestController
@RequestMapping("/api/v1")
public class SystemController {

    private final SystemService systemService;

    public SystemController(SystemService systemService) {
        this.systemService = systemService;
    }

    @Operation(summary = "健康检查（公开）")
    @GetMapping("/health")
    public ApiResponse<HealthView> health() {
        return ApiResponse.ok(systemService.health());
    }

    @Operation(summary = "系统信息")
    @GetMapping("/system/info")
    public ApiResponse<SystemInfoView> info() {
        return ApiResponse.ok(systemService.info());
    }

    @Operation(summary = "存储统计")
    @GetMapping("/system/storage")
    public ApiResponse<StorageStatsView> storage() {
        return ApiResponse.ok(systemService.storage());
    }
}
