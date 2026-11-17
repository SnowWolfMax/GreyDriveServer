package com.greydrive.user;

import com.greydrive.common.ApiResponse;
import com.greydrive.security.CurrentUser;
import com.greydrive.user.dto.AuthDtos.MfaCodeRequest;
import com.greydrive.user.dto.AuthDtos.MfaDisableRequest;
import com.greydrive.user.dto.AuthDtos.MfaEnableResponse;
import com.greydrive.user.dto.AuthDtos.MfaRecoveryRequest;
import com.greydrive.user.dto.AuthDtos.MfaRecoveryResponse;
import com.greydrive.user.dto.AuthDtos.MfaSetupRequest;
import com.greydrive.user.dto.AuthDtos.MfaSetupResponse;
import com.greydrive.user.dto.AuthDtos.MfaStatusResponse;
import com.greydrive.user.dto.AuthDtos.MfaVerifyRequest;
import com.greydrive.user.dto.AuthDtos.LoginResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@Tag(name = "二次验证", description = "TOTP 绑定、登录挑战、恢复码")
@RestController
@RequestMapping("/api/v1")
public class MfaController {

    private final AuthService authService;

    public MfaController(AuthService authService) {
        this.authService = authService;
    }

    @Operation(summary = "获取 TOTP 密钥与二维码（支持 setup 令牌或登录态）")
    @PostMapping("/auth/mfa/setup")
    public ApiResponse<MfaSetupResponse> setup(@RequestBody(required = false) MfaSetupRequest request) {
        Long userId = CurrentUser.idOrNull();
        return ApiResponse.ok(authService.mfaSetup(userId, request == null ? null : request.mfaToken()));
    }

    @Operation(summary = "确认验证码并启用二次验证（返回恢复码）")
    @PostMapping("/auth/mfa/enable")
    public ApiResponse<MfaEnableResponse> enable(@Valid @RequestBody MfaCodeRequest request) {
        Long userId = CurrentUser.idOrNull();
        return ApiResponse.ok(authService.mfaEnable(userId, request.mfaToken(), request.code()));
    }

    @Operation(summary = "登录二次验证")
    @PostMapping("/auth/mfa/verify")
    public ApiResponse<LoginResponse> verify(@Valid @RequestBody MfaVerifyRequest request) {
        return ApiResponse.ok(authService.mfaVerify(request.mfaToken(), request.code()));
    }

    @Operation(summary = "查询当前用户二次验证状态")
    @GetMapping("/users/me/mfa")
    public ApiResponse<MfaStatusResponse> status() {
        return ApiResponse.ok(authService.mfaStatus(CurrentUser.id()));
    }

    @Operation(summary = "关闭二次验证")
    @PostMapping("/users/me/mfa/disable")
    public ApiResponse<Void> disable(@Valid @RequestBody MfaDisableRequest request) {
        authService.disableMfa(CurrentUser.id(), request.password(), request.code());
        return ApiResponse.ok();
    }

    @Operation(summary = "重新生成恢复码")
    @PostMapping("/users/me/mfa/recovery-codes")
    public ApiResponse<MfaRecoveryResponse> regenerate(@Valid @RequestBody MfaRecoveryRequest request) {
        return ApiResponse.ok(new MfaRecoveryResponse(
                authService.regenerateRecoveryCodes(CurrentUser.id(), request.code())));
    }
}
