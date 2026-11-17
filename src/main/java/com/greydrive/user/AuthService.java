package com.greydrive.user;

import com.greydrive.common.BizException;
import com.greydrive.common.ErrorCode;
import com.greydrive.common.WebRequestInfo;
import com.greydrive.config.GreyDriveProperties;
import com.greydrive.mfa.MfaService;
import com.greydrive.oplog.OperationLogService;
import com.greydrive.security.JwtService;
import com.greydrive.user.dto.AuthDtos.ChangePasswordRequest;
import com.greydrive.user.dto.AuthDtos.LoginRequest;
import com.greydrive.user.dto.AuthDtos.LoginResponse;
import com.greydrive.user.dto.AuthDtos.MfaEnableResponse;
import com.greydrive.user.dto.AuthDtos.MfaSetupResponse;
import com.greydrive.user.dto.AuthDtos.MfaStatusResponse;
import com.greydrive.user.dto.AuthDtos.RefreshRequest;
import com.greydrive.user.dto.AuthDtos.RegisterRequest;
import com.greydrive.user.dto.AuthDtos.SessionView;
import com.greydrive.user.dto.AuthDtos.TokenPairResponse;
import com.greydrive.user.dto.AuthDtos.UpdateProfileRequest;
import com.greydrive.user.dto.AuthDtos.UserView;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final MfaService mfaService;
    private final GreyDriveProperties properties;
    private final OperationLogService operationLogService;
    private final RefreshTokenService refreshTokenService;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder, JwtService jwtService,
                       MfaService mfaService, GreyDriveProperties properties,
                       OperationLogService operationLogService, RefreshTokenService refreshTokenService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.mfaService = mfaService;
        this.properties = properties;
        this.operationLogService = operationLogService;
        this.refreshTokenService = refreshTokenService;
    }

    @Transactional
    public UserView register(RegisterRequest request) {
        if (userRepository.existsByUsername(request.username())) {
            throw new BizException(ErrorCode.USERNAME_EXISTS);
        }
        if (request.email() != null && !request.email().isBlank() && userRepository.existsByEmail(request.email())) {
            throw new BizException(ErrorCode.CONFLICT, "邮箱已被使用");
        }
        User user = new User();
        user.setUsername(request.username());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setNickname(request.nickname() == null || request.nickname().isBlank()
                ? request.username() : request.nickname());
        user.setEmail(request.email() == null || request.email().isBlank() ? null : request.email());
        user.setStatus("ACTIVE");
        user = userRepository.save(user);
        operationLogService.record(user.getId(), "REGISTER", "USER", user.getId(), null,
                WebRequestInfo.clientIp(), WebRequestInfo.userAgent(), true);
        return toView(user);
    }

    @Transactional
    public LoginResponse login(LoginRequest request) {
        User user = userRepository.findByUsername(request.username())
                .orElseThrow(() -> new BizException(ErrorCode.BAD_CREDENTIALS));
        if (!"ACTIVE".equals(user.getStatus())) {
            throw new BizException(ErrorCode.FORBIDDEN, "账号已停用");
        }
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new BizException(ErrorCode.BAD_CREDENTIALS);
        }
        if (mfaService.isEnabled(user.getId())) {
            return new LoginResponse(null, "Bearer", 0, toView(user), true, false,
                    jwtService.issueMfaToken(user.getId(), "verify"), null);
        }
        if (properties.getSecurity().isMfaRequired()) {
            return new LoginResponse(null, "Bearer", 0, toView(user), false, true,
                    jwtService.issueMfaToken(user.getId(), "setup"), null);
        }
        return completeLogin(user);
    }

    @Transactional
    public MfaSetupResponse mfaSetup(Long userId, String mfaToken) {
        long uid = resolveMfaUser(userId, mfaToken, "setup");
        MfaService.SetupResult result = mfaService.setup(findUser(uid));
        return new MfaSetupResponse(result.secret(), result.otpauthUri(), result.qrDataUrl());
    }

    @Transactional
    public MfaEnableResponse mfaEnable(Long userId, String mfaToken, String code) {
        long uid = resolveMfaUser(userId, mfaToken, "setup");
        User user = findUser(uid);
        List<String> recoveryCodes = mfaService.enable(user, code);
        operationLogService.record(uid, "MFA_ENABLE", "USER", uid, null,
                WebRequestInfo.clientIp(), WebRequestInfo.userAgent(), true);
        String token = jwtService.issueUserToken(user.getId(), user.getUsername());
        String refreshToken = refreshTokenService.issue(user.getId());
        return new MfaEnableResponse(token, "Bearer", properties.getSecurity().getJwtTtl().toSeconds(),
                toView(user), recoveryCodes, refreshToken);
    }

    @Transactional
    public LoginResponse mfaVerify(String mfaToken, String code) {
        JwtService.MfaPrincipal principal;
        try {
            principal = jwtService.parseMfaToken(mfaToken);
        } catch (Exception e) {
            throw new BizException(ErrorCode.MFA_TOKEN_INVALID);
        }
        if (!"verify".equals(principal.purpose())) {
            throw new BizException(ErrorCode.MFA_TOKEN_INVALID);
        }
        if (!mfaService.verify(principal.userId(), code)) {
            operationLogService.record(principal.userId(), "MFA_VERIFY_FAILED", "USER", principal.userId(), null,
                    WebRequestInfo.clientIp(), WebRequestInfo.userAgent(), false);
            throw new BizException(ErrorCode.MFA_CODE_INVALID);
        }
        operationLogService.record(principal.userId(), "MFA_VERIFY", "USER", principal.userId(), null,
                WebRequestInfo.clientIp(), WebRequestInfo.userAgent(), true);
        return completeLogin(findUser(principal.userId()));
    }

    @Transactional(readOnly = true)
    public MfaStatusResponse mfaStatus(long userId) {
        MfaService.Status status = mfaService.status(userId);
        return new MfaStatusResponse(status.enabled(), status.unusedRecoveryCodes());
    }

    @Transactional
    public void disableMfa(long userId, String password, String code) {
        User user = findUser(userId);
        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            throw new BizException(ErrorCode.BAD_CREDENTIALS, "密码错误");
        }
        if (!mfaService.verify(userId, code)) {
            throw new BizException(ErrorCode.MFA_CODE_INVALID);
        }
        mfaService.disable(userId);
        operationLogService.record(userId, "MFA_DISABLE", "USER", userId, null,
                WebRequestInfo.clientIp(), WebRequestInfo.userAgent(), true);
    }

    @Transactional
    public List<String> regenerateRecoveryCodes(long userId, String code) {
        List<String> codes = mfaService.regenerateRecoveryCodes(userId, code);
        operationLogService.record(userId, "MFA_RECOVERY_REGENERATE", "USER", userId, null,
                WebRequestInfo.clientIp(), WebRequestInfo.userAgent(), true);
        return codes;
    }

    @Transactional(readOnly = true)
    public UserView me(long userId) {
        return toView(findUser(userId));
    }

    @Transactional
    public UserView updateProfile(long userId, UpdateProfileRequest request) {
        User user = findUser(userId);
        if (request.nickname() != null) {
            user.setNickname(request.nickname());
        }
        if (request.email() != null && !request.email().isBlank()) {
            if (!request.email().equals(user.getEmail()) && userRepository.existsByEmail(request.email())) {
                throw new BizException(ErrorCode.CONFLICT, "邮箱已被使用");
            }
            user.setEmail(request.email());
        }
        user = userRepository.save(user);
        operationLogService.record(userId, "USER_UPDATE", "USER", userId, null,
                WebRequestInfo.clientIp(), WebRequestInfo.userAgent(), true);
        return toView(user);
    }

    @Transactional
    public void changePassword(long userId, ChangePasswordRequest request) {
        User user = findUser(userId);
        if (!passwordEncoder.matches(request.oldPassword(), user.getPasswordHash())) {
            throw new BizException(ErrorCode.BAD_CREDENTIALS, "原密码错误");
        }
        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        userRepository.save(user);
        refreshTokenService.revokeAll(userId);
        operationLogService.record(userId, "PASSWORD_CHANGE", "USER", userId, null,
                WebRequestInfo.clientIp(), WebRequestInfo.userAgent(), true);
    }

    @Transactional
    public void logout(long userId, String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            refreshTokenService.revokeAll(userId);
        } else {
            refreshTokenService.revoke(refreshToken);
        }
        operationLogService.record(userId, "LOGOUT", "USER", userId, null,
                WebRequestInfo.clientIp(), WebRequestInfo.userAgent(), true);
    }

    @Transactional
    public TokenPairResponse refresh(RefreshRequest request) {
        RefreshTokenService.Rotation rotation = refreshTokenService.rotate(request.refreshToken());
        User user = findUser(rotation.userId());
        String accessToken = jwtService.issueUserToken(user.getId(), user.getUsername());
        return new TokenPairResponse(accessToken, rotation.refreshToken(), "Bearer",
                properties.getSecurity().getJwtTtl().toSeconds());
    }

    @Transactional(readOnly = true)
    public List<SessionView> sessions(long userId) {
        return refreshTokenService.sessions(userId);
    }

    @Transactional
    public void revokeSession(long userId, long sessionId) {
        refreshTokenService.revokeSession(userId, sessionId);
    }

    public User findUser(long userId) {
        return userRepository.findById(userId).orElseThrow(() -> new BizException(ErrorCode.UNAUTHORIZED));
    }

    private LoginResponse completeLogin(User user) {
        user.setLastLoginAt(Instant.now());
        userRepository.save(user);
        String token = jwtService.issueUserToken(user.getId(), user.getUsername());
        String refreshToken = refreshTokenService.issue(user.getId());
        operationLogService.record(user.getId(), "LOGIN", "USER", user.getId(), null,
                WebRequestInfo.clientIp(), WebRequestInfo.userAgent(), true);
        return new LoginResponse(token, "Bearer", properties.getSecurity().getJwtTtl().toSeconds(),
                toView(user), false, false, null, refreshToken);
    }

    private long resolveMfaUser(Long userId, String mfaToken, String expectedPurpose) {
        if (mfaToken != null && !mfaToken.isBlank()) {
            try {
                JwtService.MfaPrincipal principal = jwtService.parseMfaToken(mfaToken);
                if (!expectedPurpose.equals(principal.purpose())) {
                    throw new BizException(ErrorCode.MFA_TOKEN_INVALID);
                }
                return principal.userId();
            } catch (BizException e) {
                throw e;
            } catch (Exception e) {
                throw new BizException(ErrorCode.MFA_TOKEN_INVALID);
            }
        }
        if (userId != null) {
            return userId;
        }
        throw new BizException(ErrorCode.MFA_TOKEN_INVALID);
    }

    public static UserView toView(User user) {
        return new UserView(user.getId(), user.getUsername(), user.getNickname(), user.getEmail(),
                user.getStatus(), user.getCreatedAt());
    }
}
