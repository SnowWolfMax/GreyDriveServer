package com.greydrive.security;

import com.greydrive.config.GreyDriveProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Jwts;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Date;

@Service
public class JwtService {

    public static final String SCOPE = "scope";
    public static final String SCOPE_USER = "user";
    public static final String SCOPE_SHARE = "share";
    public static final String SCOPE_MFA = "mfa";

    private final JwtKeyProvider keyProvider;
    private final GreyDriveProperties properties;

    public JwtService(JwtKeyProvider keyProvider, GreyDriveProperties properties) {
        this.keyProvider = keyProvider;
        this.properties = properties;
    }

    public String issueUserToken(long userId, String username) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("username", username)
                .claim(SCOPE, SCOPE_USER)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(properties.getSecurity().getJwtTtl())))
                .signWith(keyProvider.key())
                .compact();
    }

    public String issueShareToken(long shareId, String shareToken) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject("share")
                .claim(SCOPE, SCOPE_SHARE)
                .claim("sid", shareId)
                .claim("st", shareToken)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(properties.getSecurity().getShareAccessTtl())))
                .signWith(keyProvider.key())
                .compact();
    }

    public String issueMfaToken(long userId, String purpose) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim(SCOPE, SCOPE_MFA)
                .claim("purpose", purpose)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(properties.getSecurity().getMfaChallengeTtl())))
                .signWith(keyProvider.key())
                .compact();
    }

    public MfaPrincipal parseMfaToken(String token) {
        Claims claims = parse(token);
        if (!SCOPE_MFA.equals(claims.get(SCOPE, String.class))) {
            throw new IllegalArgumentException("不是二次验证令牌");
        }
        return new MfaPrincipal(Long.parseLong(claims.getSubject()),
                claims.get("purpose", String.class), claims.getExpiration().toInstant());
    }

    public JwtPrincipal parseUserToken(String token) {
        Claims claims = parse(token);
        if (!SCOPE_USER.equals(claims.get(SCOPE, String.class))) {
            throw new IllegalArgumentException("不是用户令牌");
        }
        return new JwtPrincipal(Long.parseLong(claims.getSubject()),
                claims.get("username", String.class), claims.getExpiration().toInstant());
    }

    public SharePrincipal parseShareToken(String token) {
        Claims claims = parse(token);
        if (!SCOPE_SHARE.equals(claims.get(SCOPE, String.class))) {
            throw new IllegalArgumentException("不是分享令牌");
        }
        return new SharePrincipal(Long.parseLong(String.valueOf(claims.get("sid"))),
                claims.get("st", String.class), claims.getExpiration().toInstant());
    }

    private Claims parse(String token) {
        Jws<Claims> jws = Jwts.parser().verifyWith(keyProvider.key()).build().parseSignedClaims(token);
        return jws.getPayload();
    }

    public record JwtPrincipal(Long userId, String username, Instant expiresAt) {
    }

    public record SharePrincipal(Long shareId, String shareToken, Instant expiresAt) {
    }

    public record MfaPrincipal(Long userId, String purpose, Instant expiresAt) {
    }
}
