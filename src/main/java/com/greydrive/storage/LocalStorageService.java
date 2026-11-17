package com.greydrive.storage;

import org.springframework.stereotype.Service;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

@Service
public class LocalStorageService implements StorageService {

    private final StoragePaths paths;

    public LocalStorageService(StoragePaths paths) {
        this.paths = paths;
    }

    @Override
    public void store(String key, Path source) throws IOException {
        Path target = path(key);
        Files.createDirectories(target.getParent());
        if (Files.exists(target)) {
            Files.deleteIfExists(source);
            return;
        }
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    @Override
    public Path path(String key) {
        Path resolved = paths.blobsDir().resolve(key).normalize();
        if (!resolved.startsWith(paths.blobsDir())) {
            throw new IllegalArgumentException("非法存储键");
        }
        return resolved;
    }

    @Override
    public boolean exists(String key) {
        return Files.isRegularFile(path(key));
    }

    @Override
    public long size(String key) throws IOException {
        return Files.size(path(key));
    }

    @Override
    public void delete(String key) throws IOException {
        Files.deleteIfExists(path(key));
    }

    @Override
    public InputStream open(String key, long offset, long length) throws IOException {
        InputStream in = Files.newInputStream(path(key));
        try {
            in.skipNBytes(offset);
        } catch (IOException e) {
            in.close();
            throw e;
        }
        return new BoundedInputStream(in, length);
    }

    private static final class BoundedInputStream extends FilterInputStream {
        private long remaining;

        private BoundedInputStream(InputStream in, long remaining) {
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
            int toRead = (int) Math.min(len, remaining);
            int read = super.read(b, off, toRead);
            if (read > 0) {
                remaining -= read;
            }
            return read;
        }

        @Override
        public long skip(long n) throws IOException {
            long skipped = super.skip(Math.min(n, remaining));
            remaining -= skipped;
            return skipped;
        }

        @Override
        public int available() throws IOException {
            return (int) Math.min(super.available(), remaining);
        }
    }
}
