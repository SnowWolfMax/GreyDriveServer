package com.greydrive.user;

import com.greydrive.common.ApiResponse;
import com.greydrive.security.CurrentUser;
import com.greydrive.user.dto.AuthDtos.ChangePasswordRequest;
import com.greydrive.user.dto.AuthDtos.SessionView;
import com.greydrive.user.dto.AuthDtos.UpdateProfileRequest;
import com.greydrive.user.dto.AuthDtos.UserView;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "用户", description = "当前用户资料")
@RestController
@RequestMapping("/api/v1/users")
public class UserController {

    private final AuthService authService;

    public UserController(AuthService authService) {
        this.authService = authService;
    }

    @Operation(summary = "获取当前用户")
    @GetMapping("/me")
    public ApiResponse<UserView> me() {
        return ApiResponse.ok(authService.me(CurrentUser.id()));
    }

    @Operation(summary = "更新当前用户资料")
    @PutMapping("/me")
    public ApiResponse<UserView> update(@Valid @RequestBody UpdateProfileRequest request) {
        return ApiResponse.ok(authService.updateProfile(CurrentUser.id(), request));
    }

    @Operation(summary = "修改密码")
    @PutMapping("/me/password")
    public ApiResponse<Void> changePassword(@Valid @RequestBody ChangePasswordRequest request) {
        authService.changePassword(CurrentUser.id(), request);
        return ApiResponse.ok();
    }

    @Operation(summary = "当前账号的登录设备")
    @GetMapping("/me/sessions")
    public ApiResponse<List<SessionView>> sessions() {
        return ApiResponse.ok(authService.sessions(CurrentUser.id()));
    }

    @Operation(summary = "撤销指定设备的登录状态")
    @DeleteMapping("/me/sessions/{id}")
    public ApiResponse<Void> revokeSession(@PathVariable long id) {
        authService.revokeSession(CurrentUser.id(), id);
        return ApiResponse.ok();
    }
}
