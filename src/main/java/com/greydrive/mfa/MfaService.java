package com.greydrive.mfa;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.greydrive.common.BizException;
import com.greydrive.common.ErrorCode;
import com.greydrive.config.GreyDriveProperties;
import com.greydrive.security.totp.Totp;
import com.greydrive.user.User;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

@Service
public class MfaService {

    private static final String RECOVERY_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int RECOVERY_CODE_COUNT = 10;

    private final UserMfaRepository mfaRepository;
    private final MfaRecoveryCodeRepository recoveryCodeRepository;
    private final PasswordEncoder passwordEncoder;
    private final GreyDriveProperties properties;

    public MfaService(UserMfaRepository mfaRepository, MfaRecoveryCodeRepository recoveryCodeRepository,
                      PasswordEncoder passwordEncoder, GreyDriveProperties properties) {
        this.mfaRepository = mfaRepository;
        this.recoveryCodeRepository = recoveryCodeRepository;
        this.passwordEncoder = passwordEncoder;
        this.properties = properties;
    }

    @Transactional(readOnly = true)
    public boolean isEnabled(long userId) {
        return mfaRepository.findById(userId).map(UserMfa::isEnabled).orElse(false);
    }

    @Transactional
    public SetupResult setup(User user) {
        UserMfa mfa = mfaRepository.findById(user.getId()).orElse(null);
        if (mfa != null && mfa.isEnabled()) {
            throw new BizException(ErrorCode.MFA_ALREADY_ENABLED);
        }
        if (mfa == null) {
            mfa = new UserMfa();
            mfa.setUserId(user.getId());
            mfa.setCreatedAt(Instant.now());
        }
        mfa.setSecret(Totp.randomSecret(20));
        mfa.setEnabled(false);
        mfa.setConfirmedAt(null);
        mfaRepository.save(mfa);

        String issuer = properties.getSecurity().getMfaIssuer();
        String label = urlEncode(issuer + ":" + user.getUsername());
        String uri = "otpauth://totp/" + label
                + "?secret=" + mfa.getSecret()
                + "&issuer=" + urlEncode(issuer)
                + "&algorithm=SHA1&digits=6&period=30";
        return new SetupResult(mfa.getSecret(), uri, qrDataUrl(uri));
    }

    @Transactional
    public List<String> enable(User user, String code) {
        UserMfa mfa = mfaRepository.findById(user.getId())
                .orElseThrow(() -> new BizException(ErrorCode.MFA_CODE_INVALID, "请先获取二次验证密钥"));
        if (mfa.isEnabled()) {
            throw new BizException(ErrorCode.MFA_ALREADY_ENABLED);
        }
        if (!Totp.verify(mfa.getSecret(), code)) {
            throw new BizException(ErrorCode.MFA_CODE_INVALID);
        }
        mfa.setEnabled(true);
        mfa.setConfirmedAt(Instant.now());
        mfaRepository.save(mfa);
        return issueRecoveryCodes(user.getId());
    }

    @Transactional
    public boolean verify(long userId, String code) {
        UserMfa mfa = mfaRepository.findById(userId).orElse(null);
        if (mfa == null || !mfa.isEnabled()) {
            return false;
        }
        if (Totp.verify(mfa.getSecret(), code)) {
            return true;
        }
        String normalized = Totp.normalizeRecoveryCode(code);
        if (normalized.isEmpty()) {
            return false;
        }
        for (MfaRecoveryCode recovery : recoveryCodeRepository.findByUserId(userId)) {
            if (!recovery.isUsed() && passwordEncoder.matches(normalized, recovery.getCodeHash())) {
                recovery.setUsed(true);
                recoveryCodeRepository.save(recovery);
                return true;
            }
        }
        return false;
    }

    @Transactional
    public void disable(long userId) {
        mfaRepository.findById(userId).ifPresent(mfaRepository::delete);
        recoveryCodeRepository.deleteByUserId(userId);
    }

    @Transactional
    public List<String> regenerateRecoveryCodes(long userId, String code) {
        if (!verify(userId, code)) {
            throw new BizException(ErrorCode.MFA_CODE_INVALID);
        }
        recoveryCodeRepository.deleteByUserId(userId);
        return issueRecoveryCodes(userId);
    }

    @Transactional(readOnly = true)
    public Status status(long userId) {
        boolean enabled = mfaRepository.findById(userId).map(UserMfa::isEnabled).orElse(false);
        long unused = enabled ? recoveryCodeRepository.countByUserIdAndUsedFalse(userId) : 0;
        return new Status(enabled, unused);
    }

    private List<String> issueRecoveryCodes(long userId) {
        recoveryCodeRepository.deleteByUserId(userId);
        List<String> plain = new ArrayList<>();
        Instant now = Instant.now();
        for (int i = 0; i < RECOVERY_CODE_COUNT; i++) {
            String raw = randomRecoveryCode(8);
            plain.add(raw.substring(0, 4) + "-" + raw.substring(4));
            MfaRecoveryCode code = new MfaRecoveryCode();
            code.setUserId(userId);
            code.setCodeHash(passwordEncoder.encode(raw));
            code.setUsed(false);
            code.setCreatedAt(now);
            recoveryCodeRepository.save(code);
        }
        return plain;
    }

    private String randomRecoveryCode(int length) {
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(RECOVERY_ALPHABET.charAt(RANDOM.nextInt(RECOVERY_ALPHABET.length())));
        }
        return sb.toString();
    }

    private String qrDataUrl(String content) {
        try {
            BitMatrix matrix = new QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, 280, 280);
            BufferedImage image = MatrixToImageWriter.toBufferedImage(matrix);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(image, "png", out);
            return "data:image/png;base64," + Base64.getEncoder().encodeToString(out.toByteArray());
        } catch (Exception e) {
            throw new IllegalStateException("生成二维码失败", e);
        }
    }

    private String urlEncode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    public record SetupResult(String secret, String otpauthUri, String qrDataUrl) {
    }

    public record Status(boolean enabled, long unusedRecoveryCodes) {
    }
}
