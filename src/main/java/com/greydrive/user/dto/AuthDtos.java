package com.greydrive.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;

public final class AuthDtos {

    private AuthDtos() {
    }

    public record RegisterRequest(
            @NotBlank @Size(min = 3, max = 64)
            @Pattern(regexp = "^[a-zA-Z0-9_]+$", message = "用户名只能包含字母、数字和下划线")
            String username,
            @NotBlank @Size(min = 8, max = 72) String password,
            @Size(max = 64) String nickname,
            @Email @Size(max = 128) String email) {
    }

    public record LoginRequest(@NotBlank String username, @NotBlank String password) {
    }

    public record UserView(Long id, String username, String nickname, String email, String status,
                           Instant createdAt) {
    }

    public record LoginResponse(String accessToken, String tokenType, long expiresIn, UserView user,
                                boolean mfaRequired, boolean mfaSetupRequired, String mfaToken,
                                String refreshToken) {
    }

    public record RefreshRequest(@NotBlank String refreshToken) {
    }

    public record TokenPairResponse(String accessToken, String refreshToken, String tokenType, long expiresIn) {
    }

    public record LogoutRequest(String refreshToken) {
    }

    public record SessionView(Long id, String deviceName, String ip, String userAgent,
                              Instant createdAt, Instant lastUsedAt, Instant expiresAt) {
    }

    public record UpdateProfileRequest(@Size(max = 64) String nickname,
                                       @Email @Size(max = 128) String email) {
    }

    public record ChangePasswordRequest(@NotBlank String oldPassword,
                                        @NotBlank @Size(min = 8, max = 72) String newPassword) {
    }

    public record MfaSetupRequest(String mfaToken) {
    }

    public record MfaCodeRequest(String mfaToken, @NotBlank String code) {
    }

    public record MfaVerifyRequest(@NotBlank String mfaToken, @NotBlank String code) {
    }

    public record MfaSetupResponse(String secret, String otpauthUri, String qrDataUrl) {
    }

    public record MfaEnableResponse(String accessToken, String tokenType, long expiresIn, UserView user,
                                    List<String> recoveryCodes, String refreshToken) {
    }

    public record MfaStatusResponse(boolean enabled, long unusedRecoveryCodes) {
    }

    public record MfaDisableRequest(@NotBlank String password, @NotBlank String code) {
    }

    public record MfaRecoveryRequest(@NotBlank String code) {
    }

    public record MfaRecoveryResponse(List<String> recoveryCodes) {
    }
}
