package com.greydrive.bootstrap;

import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.FileSystemResource;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

/**
 * 首次运行初始化：复制默认配置到 ./config/application.yml，并创建数据目录。
 * 已存在的配置与数据一律不覆盖，保证重复启动幂等。
 */
public final class Bootstrap {

    private Bootstrap() {
    }

    public static void init() {
        Path cwd = Paths.get("").toAbsolutePath().normalize();
        Path configDir = cwd.resolve("config");
        Path configFile = configDir.resolve("application.yml");
        try {
            Files.createDirectories(configDir);
            if (Files.notExists(configFile)) {
                try (InputStream in = Bootstrap.class.getResourceAsStream("/application.yml")) {
                    if (in == null) {
                        throw new IllegalStateException("classpath:/application.yml not found");
                    }
                    Files.copy(in, configFile);
                }
            }
        } catch (IOException e) {
            throw new IllegalStateException("无法初始化配置文件: " + configFile, e);
        }

        String dataDir = resolveDataDir(configFile);
        Path data = cwd.resolve(dataDir).normalize();
        try {
            for (String sub : List.of("", "db", "storage", "storage/blobs", "chunks", "thumbnails", "tmp", "secrets", "logs")) {
                Files.createDirectories(data.resolve(sub));
            }
        } catch (IOException e) {
            throw new IllegalStateException("无法初始化数据目录: " + data, e);
        }
    }

    private static String resolveDataDir(Path configFile) {
        try {
            YamlPropertySourceLoader loader = new YamlPropertySourceLoader();
            List<PropertySource<?>> sources = loader.load("bootstrap", new FileSystemResource(configFile));
            for (PropertySource<?> source : sources) {
                Object value = source.getProperty("greydrive.data-dir");
                if (value != null && !value.toString().isBlank()) {
                    return value.toString();
                }
            }
        } catch (Exception ignored) {
            // 配置解析失败时使用默认目录，Spring 启动阶段会给出明确错误
        }
        return "./data";
    }
}
