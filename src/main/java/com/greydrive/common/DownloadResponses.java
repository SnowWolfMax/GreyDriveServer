package com.greydrive.common;

import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public final class DownloadResponses {

    private DownloadResponses() {
    }

    public static ResponseEntity<?> build(Path file, String contentType, String fileName, String rangeHeader)
            throws IOException {
        long total = Files.size(file);
        String mime = contentType == null || contentType.isBlank() ? "application/octet-stream" : contentType;
        Resource resource = new FileSystemResource(file);

        HttpHeaders baseHeaders = new HttpHeaders();
        baseHeaders.set(HttpHeaders.ACCEPT_RANGES, "bytes");
        baseHeaders.set(HttpHeaders.CONTENT_DISPOSITION, contentDisposition(fileName));
        if (total == 0) {
            return ResponseEntity.ok().headers(baseHeaders)
                    .contentLength(0).contentType(MediaType.parseMediaType(mime)).body(new byte[0]);
        }

        long start;
        long end;
        boolean partial = false;
        if (rangeHeader == null || rangeHeader.isBlank()) {
            start = 0;
            end = total - 1;
        } else {
            long[] range = parseRange(rangeHeader, total);
            if (range == null) {
                return ResponseEntity.status(HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE)
                        .header(HttpHeaders.CONTENT_RANGE, "bytes */" + total)
                        .headers(baseHeaders)
                        .build();
            }
            start = range[0];
            end = range[1];
            partial = true;
        }

        long length = end - start + 1;
        HttpHeaders headers = new HttpHeaders();
        headers.putAll(baseHeaders);
        headers.setContentLength(length);
        if (partial) {
            headers.set(HttpHeaders.CONTENT_RANGE, "bytes " + start + "-" + end + "/" + total);
            java.io.InputStream in = Files.newInputStream(file);
            in.skipNBytes(start);
            return ResponseEntity.status(HttpStatus.PARTIAL_CONTENT)
                    .headers(headers)
                    .contentType(MediaType.parseMediaType(mime))
                    .body(new InputStreamResource(new BoundedInputStream(in, length)));
        }
        return ResponseEntity.ok()
                .headers(headers)
                .contentType(MediaType.parseMediaType(mime))
                .body(resource);
    }

    private static long[] parseRange(String header, long total) {
        if (!header.startsWith("bytes=")) {
            return null;
        }
        String spec = header.substring("bytes=".length()).split(",")[0].trim();
        int dash = spec.indexOf('-');
        if (dash < 0) {
            return null;
        }
        try {
            String startPart = spec.substring(0, dash).trim();
            String endPart = spec.substring(dash + 1).trim();
            if (startPart.isEmpty()) {
                long suffix = Long.parseLong(endPart);
                if (suffix <= 0) {
                    return null;
                }
                long start = Math.max(0, total - suffix);
                return new long[]{start, total - 1};
            }
            long start = Long.parseLong(startPart);
            long end = endPart.isEmpty() ? total - 1 : Long.parseLong(endPart);
            if (start < 0 || start >= total || end < start) {
                return null;
            }
            return new long[]{start, Math.min(end, total - 1)};
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String contentDisposition(String fileName) {
        String name = fileName == null || fileName.isBlank() ? "download" : fileName;
        String encoded = URLEncoder.encode(name, StandardCharsets.UTF_8).replace("+", "%20");
        return "attachment; filename=\"" + asciiFallback(name) + "\"; filename*=UTF-8''" + encoded;
    }

    private static String asciiFallback(String name) {
        return name.replaceAll("[^\\x20-\\x7E]", "_").replace("\"", "'");
    }

    private static final class BoundedInputStream extends java.io.FilterInputStream {
        private long remaining;

        private BoundedInputStream(java.io.InputStream in, long remaining) {
            super(in);
            this.remaining = remaining;
        }

        @Override
        public int read() throws IOException {
            if (remaining <= 0) {
                return -1;
            }
            int b = super.read();
            if (b >= 0) {
                remaining--;
            }
            return b;
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            if (remaining <= 0) {
                return -1;
            }
            int read = super.read(b, off, (int) Math.min(len, remaining));
            if (read > 0) {
                remaining -= read;
            }
            return read;
        }
    }
}
