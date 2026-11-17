package com.greydrive.user;

import com.greydrive.common.BizException;
import com.greydrive.common.ErrorCode;
import com.greydrive.common.Hashing;
import com.greydrive.common.WebRequestInfo;
import com.greydrive.config.GreyDriveProperties;
import com.greydrive.user.dto.AuthDtos.SessionView;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import jakarta.servlet.http.HttpServletRequest;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.List;

@Service
public class RefreshTokenService {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String PREFIX = "gdr_";

    private final RefreshTokenRepository repository;
    private final GreyDriveProperties properties;

    public RefreshTokenService(RefreshTokenRepository repository, GreyDriveProperties properties) {
        this.repository = repository;
        this.properties = properties;
    }

    @Transactional
    public String issue(long userId) {
        String raw = newToken();
        RefreshToken token = new RefreshToken();
        token.setUserId(userId);
        token.setTokenHash(Hashing.sha256Hex(raw.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        token.setDeviceName(deviceName());
        token.setUserAgent(WebRequestInfo.userAgent());
        token.setIp(WebRequestInfo.clientIp());
        token.setCreatedAt(Instant.now());
        token.setExpiresAt(Instant.now().plus(properties.getSecurity().getRefreshTtl()));
        repository.save(token);
        return raw;
    }

    @Transactional
    public Rotation rotate(String raw) {
        RefreshToken token = repository.findByTokenHash(hash(raw))
                .orElseThrow(() -> new BizException(ErrorCode.TOKEN_INVALID));
        Instant now = Instant.now();
        if (!token.isActive(now)) {
            throw new BizException(ErrorCode.TOKEN_INVALID);
        }
        token.setRevokedAt(now);
        token.setLastUsedAt(now);
        repository.save(token);
        return new Rotation(token.getUserId(), issue(token.getUserId()));
    }

    @Transactional
    public void revoke(String raw) {
        repository.findByTokenHash(hash(raw)).ifPresent(token -> {
            if (token.getRevokedAt() == null) {
                token.setRevokedAt(Instant.now());
                repository.save(token);
            }
        });
    }

    @Transactional
    public void revokeAll(long userId) {
        repository.revokeAll(userId, Instant.now());
    }

    @Transactional(readOnly = true)
    public List<SessionView> sessions(long userId) {
        return repository.findByUserIdAndRevokedAtIsNullAndExpiresAtAfterOrderByLastUsedAtDesc(
                        userId, Instant.now())
                .stream()
                .map(token -> new SessionView(token.getId(), token.getDeviceName(), token.getIp(),
                        token.getUserAgent(), token.getCreatedAt(), token.getLastUsedAt(), token.getExpiresAt()))
                .toList();
    }

    @Transactional
    public void revokeSession(long userId, long sessionId) {
        RefreshToken token = repository.findByIdAndUserId(sessionId, userId)
                .orElseThrow(() -> new BizException(ErrorCode.NOT_FOUND));
        if (token.getRevokedAt() == null) {
            token.setRevokedAt(Instant.now());
            repository.save(token);
        }
    }

    @Transactional
    public int cleanupExpired() {
        return repository.deleteStale(Instant.now().minus(properties.getSecurity().getRefreshTtl()));
    }

    private String hash(String raw) {
        return Hashing.sha256Hex(raw.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    private String newToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String deviceName() {
        HttpServletRequest request = currentRequest();
        if (request != null) {
            String name = request.getHeader("X-Device-Name");
            if (name != null && !name.isBlank()) {
                return truncate(name, 128);
            }
        }
        String ua = WebRequestInfo.userAgent();
        return ua == null || ua.isBlank() ? "Unknown device" : truncate(ua, 128);
    }

    private HttpServletRequest currentRequest() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            return attributes.getRequest();
        }
        return null;
    }

    private String truncate(String value, int max) {
        return value.length() > max ? value.substring(0, max) : value;
    }

    public record Rotation(long userId, String refreshToken) {
    }
}
