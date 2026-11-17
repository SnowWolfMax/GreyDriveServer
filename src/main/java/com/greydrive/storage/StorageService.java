package com.greydrive.storage;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;

/**
 * 文件内容存储抽象。默认本地磁盘实现；未来可替换为 S3/MinIO/OSS。
 */
public interface StorageService {

    /** 将本地临时文件落盘为指定 key，若已存在则视为去重命中。 */
    void store(String key, Path source) throws IOException;

    Path path(String key);

    boolean exists(String key);

    long size(String key) throws IOException;

    void delete(String key) throws IOException;

    InputStream open(String key, long offset, long length) throws IOException;
}
