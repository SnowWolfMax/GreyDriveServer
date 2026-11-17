package com.greydrive.user;

import com.greydrive.common.ApiResponse;
import com.greydrive.security.CurrentUser;
import com.greydrive.user.dto.AuthDtos.LoginRequest;
import com.greydrive.user.dto.AuthDtos.LoginResponse;
import com.greydrive.user.dto.AuthDtos.LogoutRequest;
import com.greydrive.user.dto.AuthDtos.RefreshRequest;
import com.greydrive.user.dto.AuthDtos.RegisterRequest;
import com.greydrive.user.dto.AuthDtos.TokenPairResponse;
import com.greydrive.user.dto.AuthDtos.UserView;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "认证", description = "注册、登录、登出")
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @Operation(summary = "注册新用户")
    @PostMapping("/register")
    public ApiResponse<UserView> register(@Valid @RequestBody RegisterRequest request) {
        return ApiResponse.ok(authService.register(request));
    }

    @Operation(summary = "登录并获取 JWT")
    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return ApiResponse.ok(authService.login(request));
    }

    @Operation(summary = "使用 refresh token 换取新的令牌对")
    @PostMapping("/refresh")
    public ApiResponse<TokenPairResponse> refresh(@Valid @RequestBody RefreshRequest request) {
        return ApiResponse.ok(authService.refresh(request));
    }

    @Operation(summary = "登出并撤销 refresh token")
    @PostMapping("/logout")
    public ApiResponse<Void> logout(@RequestBody(required = false) LogoutRequest request) {
        authService.logout(CurrentUser.id(), request == null ? null : request.refreshToken());
        return ApiResponse.ok();
    }
}
