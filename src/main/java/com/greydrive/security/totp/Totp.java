package com.greydrive.security.totp;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayOutputStream;
import java.security.SecureRandom;
import java.util.Locale;

/**
 * RFC 6238 TOTP（HMAC-SHA1，30 秒，6 位）与 Base32 编解码。
 */
public final class Totp {

    private static final String ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int PERIOD_SECONDS = 30;
    private static final int DIGITS = 6;

    private Totp() {
    }

    public static String randomSecret(int bytes) {
        byte[] buffer = new byte[bytes];
        RANDOM.nextBytes(buffer);
        return base32Encode(buffer);
    }

    public static String generate(String base32Secret, long epochMillis) {
        long counter = epochMillis / 1000L / PERIOD_SECONDS;
        byte[] key = base32Decode(base32Secret);
        byte[] data = new byte[8];
        for (int i = 7; i >= 0; i--) {
            data[i] = (byte) (counter & 0xFF);
            counter >>>= 8;
        }
        try {
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(key, "HmacSHA1"));
            byte[] hash = mac.doFinal(data);
            int offset = hash[hash.length - 1] & 0x0F;
            int binary = ((hash[offset] & 0x7F) << 24)
                    | ((hash[offset + 1] & 0xFF) << 16)
                    | ((hash[offset + 2] & 0xFF) << 8)
                    | (hash[offset + 3] & 0xFF);
            int otp = binary % 1_000_000;
            return String.format("%0" + DIGITS + "d", otp);
        } catch (Exception e) {
            throw new IllegalStateException("无法生成 TOTP", e);
        }
    }

    public static boolean verify(String base32Secret, String code) {
        if (code == null) {
            return false;
        }
        String normalized = code.replace(" ", "").trim();
        if (!normalized.matches("\\d{6}")) {
            return false;
        }
        long now = System.currentTimeMillis();
        for (int offset = -1; offset <= 1; offset++) {
            if (generate(base32Secret, now + offset * PERIOD_SECONDS * 1000L).equals(normalized)) {
                return true;
            }
        }
        return false;
    }

    public static String normalizeRecoveryCode(String code) {
        if (code == null) {
            return "";
        }
        return code.replace("-", "").replace(" ", "").trim().toUpperCase(Locale.ROOT);
    }

    public static String base32Encode(byte[] data) {
        StringBuilder sb = new StringBuilder();
        int buffer = 0;
        int bitsLeft = 0;
        for (byte b : data) {
            buffer = (buffer << 8) | (b & 0xFF);
            bitsLeft += 8;
            while (bitsLeft >= 5) {
                sb.append(ALPHABET.charAt((buffer >> (bitsLeft - 5)) & 0x1F));
                bitsLeft -= 5;
            }
        }
        if (bitsLeft > 0) {
            sb.append(ALPHABET.charAt((buffer << (5 - bitsLeft)) & 0x1F));
        }
        return sb.toString();
    }

    public static byte[] base32Decode(String value) {
        String normalized = value.replace("=", "").replace(" ", "").toUpperCase(Locale.ROOT);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int buffer = 0;
        int bitsLeft = 0;
        for (char c : normalized.toCharArray()) {
            int index = ALPHABET.indexOf(c);
            if (index < 0) {
                throw new IllegalArgumentException("非法 Base32 字符: " + c);
            }
            buffer = (buffer << 5) | index;
            bitsLeft += 5;
            if (bitsLeft >= 8) {
                out.write((buffer >> (bitsLeft - 8)) & 0xFF);
                bitsLeft -= 8;
            }
        }
        return out.toByteArray();
    }
}
