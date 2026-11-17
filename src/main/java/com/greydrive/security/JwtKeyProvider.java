package com.greydrive.security;

import com.greydrive.config.GreyDriveProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * JWT 密钥：首次启动生成并持久化，之后一直复用；文件损坏时直接失败，避免静默换密钥导致令牌全部失效。
 */
@Component
public class JwtKeyProvider {

    private static final Logger log = LoggerFactory.getLogger(JwtKeyProvider.class);

    private final SecretKey key;

    public JwtKeyProvider(GreyDriveProperties properties) {
        Path file = Paths.get(properties.getSecurity().getJwtSecretFile()).toAbsolutePath().normalize();
        try {
            Files.createDirectories(file.getParent());
            if (Files.notExists(file)) {
                byte[] random = new byte[32];
                new SecureRandom().nextBytes(random);
                Files.writeString(file, Base64.getEncoder().encodeToString(random),
                        StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
                try {
                    Files.setPosixFilePermissions(file, PosixFilePermissions.fromString("rw-------"));
                } catch (UnsupportedOperationException ignored) {
                    // 非 POSIX 文件系统
                }
                log.info("已生成 JWT 密钥文件: {}", file);
            }
            byte[] decoded = Base64.getDecoder().decode(Files.readString(file).trim());
            if (decoded.length < 32) {
                throw new IllegalStateException("JWT 密钥长度不足 32 字节，请检查文件: " + file);
            }
            this.key = new SecretKeySpec(decoded, "HmacSHA256");
        } catch (IOException | IllegalArgumentException e) {
            throw new IllegalStateException("无法加载 JWT 密钥文件: " + file, e);
        }
    }

    public SecretKey key() {
        return key;
    }
}
