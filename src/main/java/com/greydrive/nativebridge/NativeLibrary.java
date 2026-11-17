package com.greydrive.nativebridge;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.Locale;

/**
 * Loads the optional native library bundled at {@code /native/<os>-<arch>/}.
 *
 * <p>The library is extracted to the system temp directory (keyed by its
 * content hash, so upgrades never load a stale binary) and loaded with
 * {@link System#load}. If anything goes wrong the JVM keeps running with the
 * pure-Java implementations.</p>
 *
 * <p>Modes: {@code -Dgreydrive.native.mode=auto|off} or the
 * {@code GREYDRIVE_NATIVE_MODE} environment variable.</p>
 */
public final class NativeLibrary {

    private static final Logger log = LoggerFactory.getLogger(NativeLibrary.class);

    private static volatile boolean available;
    private static volatile String detail = "not initialized";

    static {
        load();
    }

    private NativeLibrary() {
    }

    public static boolean isAvailable() {
        return available;
    }

    public static String detail() {
        return detail;
    }

    /** Called when a native call fails at runtime; the Java fallback takes over. */
    static synchronized void disable(Throwable error) {
        if (!available) {
            return;
        }
        available = false;
        detail = "disabled after runtime error: " + error;
        log.warn("Native acceleration failed at runtime, falling back to Java", error);
    }

    private static void load() {
        String mode = System.getProperty("greydrive.native.mode",
                        System.getenv().getOrDefault("GREYDRIVE_NATIVE_MODE", "auto"))
                .trim().toLowerCase(Locale.ROOT);
        if (mode.equals("off") || mode.equals("false") || mode.equals("disabled")) {
            detail = "disabled by configuration";
            log.info("Native acceleration disabled by configuration");
            return;
        }

        String platform = platform();
        if (platform == null) {
            detail = "unsupported OS/arch: " + System.getProperty("os.name") + "/" + System.getProperty("os.arch");
            log.info("Native acceleration not available ({})", detail);
            return;
        }

        String libName = libName(platform);
        String resource = "/native/" + platform + "/" + libName;
        try (InputStream in = NativeLibrary.class.getResourceAsStream(resource)) {
            if (in == null) {
                detail = "not bundled: " + resource;
                log.info("Native acceleration not bundled ({}), using Java implementation", detail);
                return;
            }
            byte[] bytes = in.readAllBytes();
            Path lib = extract(bytes, platform, libName);
            System.load(lib.toAbsolutePath().toString());
            available = true;
            detail = platform + " (" + (bytes.length / 1024) + " KB)";
            log.info("Native acceleration enabled: {}", detail);
        } catch (Throwable error) {
            available = false;
            detail = error.getClass().getSimpleName() + ": " + error.getMessage();
            log.warn("Native acceleration unavailable ({}), falling back to Java", detail);
        }
    }

    private static String platform() {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        String arch = System.getProperty("os.arch", "").toLowerCase(Locale.ROOT);
        String cpu;
        if (arch.contains("aarch64") || arch.contains("arm64")) {
            cpu = "aarch64";
        } else if (arch.contains("64")) {
            cpu = "x86_64";
        } else {
            return null;
        }
        if (os.contains("win")) {
            return "windows-" + cpu;
        }
        if (os.contains("mac")) {
            return "macos-" + cpu;
        }
        if (os.contains("linux")) {
            return "linux-" + cpu;
        }
        return null;
    }

    private static String libName(String platform) {
        if (platform.startsWith("windows")) {
            return "greydrive_native.dll";
        }
        if (platform.startsWith("macos")) {
            return "libgreydrive_native.dylib";
        }
        return "libgreydrive_native.so";
    }

    private static Path extract(byte[] bytes, String platform, String libName) throws Exception {
        String hash = hex(MessageDigest.getInstance("SHA-256").digest(bytes)).substring(0, 16);
        Path dir = Paths.get(System.getProperty("java.io.tmpdir"), "greydrive-native", hash, platform);
        Files.createDirectories(dir);
        Path lib = dir.resolve(libName);
        if (Files.isRegularFile(lib) && Files.size(lib) == bytes.length) {
            return lib;
        }
        Path temp = Files.createTempFile(dir, libName, ".tmp");
        Files.write(temp, bytes);
        try {
            Files.move(temp, lib, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException ignored) {
            Files.move(temp, lib, StandardCopyOption.REPLACE_EXISTING);
        }
        return lib;
    }

    private static String hex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(Character.forDigit((b >> 4) & 0xF, 16));
            sb.append(Character.forDigit(b & 0xF, 16));
        }
        return sb.toString();
    }
}
