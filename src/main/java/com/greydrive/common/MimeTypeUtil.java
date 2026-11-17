package com.greydrive.common;

import java.net.URLConnection;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class MimeTypeUtil {

    private static final Map<String, String> EXTRA = Map.ofEntries(
            Map.entry("md", "text/markdown"),
            Map.entry("markdown", "text/markdown"),
            Map.entry("json", "application/json"),
            Map.entry("yaml", "text/yaml"),
            Map.entry("yml", "text/yaml"),
            Map.entry("log", "text/plain"),
            Map.entry("webp", "image/webp"),
            Map.entry("heic", "image/heic"),
            Map.entry("mp4", "video/mp4"),
            Map.entry("mkv", "video/x-matroska"),
            Map.entry("mp3", "audio/mpeg"),
            Map.entry("wav", "audio/wav"),
            Map.entry("pdf", "application/pdf"),
            Map.entry("docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document"),
            Map.entry("xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"),
            Map.entry("pptx", "application/vnd.openxmlformats-officedocument.presentationml.presentation"),
            Map.entry("zip", "application/zip"),
            Map.entry("7z", "application/x-7z-compressed"),
            Map.entry("rar", "application/vnd.rar"),
            Map.entry("apk", "application/vnd.android.package-archive")
    );

    private static final Set<String> TEXT_EXT = Set.of(
            "txt", "md", "markdown", "json", "yaml", "yml", "xml", "html", "htm", "css", "js", "ts",
            "java", "kt", "py", "go", "rs", "c", "h", "cpp", "sh", "sql", "log", "csv", "ini", "conf", "properties"
    );

    private MimeTypeUtil() {
    }

    public static String extension(String fileName) {
        if (fileName == null) {
            return "";
        }
        int idx = fileName.lastIndexOf('.');
        if (idx < 0 || idx == fileName.length() - 1) {
            return "";
        }
        return fileName.substring(idx + 1).toLowerCase(Locale.ROOT);
    }

    public static String guess(String fileName) {
        String ext = extension(fileName);
        if (ext.isEmpty()) {
            return "application/octet-stream";
        }
        String extra = EXTRA.get(ext);
        if (extra != null) {
            return extra;
        }
        String guessed = URLConnection.guessContentTypeFromName(fileName);
        return guessed == null ? "application/octet-stream" : guessed;
    }

    public static boolean isImage(String mimeType, String fileName) {
        if (mimeType != null && mimeType.toLowerCase(Locale.ROOT).startsWith("image/")) {
            return true;
        }
        String ext = extension(fileName);
        return Set.of("jpg", "jpeg", "png", "gif", "bmp", "webp").contains(ext);
    }

    public static boolean isText(String mimeType, String fileName) {
        if (mimeType != null && mimeType.toLowerCase(Locale.ROOT).startsWith("text/")) {
            return true;
        }
        return TEXT_EXT.contains(extension(fileName));
    }
}
