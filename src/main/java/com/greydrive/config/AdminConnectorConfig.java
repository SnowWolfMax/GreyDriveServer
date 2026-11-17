package com.greydrive.config;

import org.apache.catalina.connector.Connector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;

/**
 * 在 server.port 之外额外监听管理后台端口（默认 80）。
 * 端口不可用时仅告警，不影响主服务启动。
 */
@Component
@ConditionalOnProperty(prefix = "greydrive.admin", name = "enabled", havingValue = "true", matchIfMissing = true)
public class AdminConnectorConfig implements WebServerFactoryCustomizer<TomcatServletWebServerFactory> {

    private static final Logger log = LoggerFactory.getLogger(AdminConnectorConfig.class);

    private final GreyDriveProperties properties;
    private final Environment environment;
    private volatile Integer boundPort;

    public AdminConnectorConfig(GreyDriveProperties properties, Environment environment) {
        this.properties = properties;
        this.environment = environment;
    }

    @Override
    public void customize(TomcatServletWebServerFactory factory) {
        int adminPort = properties.getAdmin().getPort();
        int serverPort = environment.getProperty("server.port", Integer.class, 8080);
        if (adminPort <= 0 || adminPort == serverPort) {
            return;
        }
        String address = environment.getProperty("server.address", "0.0.0.0");
        if (!available(address, adminPort)) {
            log.warn("管理端口 {} 已被占用，管理后台未启用；服务仍可通过 {} 端口访问", adminPort, serverPort);
            return;
        }
        Connector connector = new Connector(TomcatServletWebServerFactory.DEFAULT_PROTOCOL);
        connector.setPort(adminPort);
        // 与管理端口保持同一监听地址；server.address=127.0.0.1 时管理端口也仅本机可访问
        connector.setProperty("address", address);
        connector.setURIEncoding(StandardCharsets.UTF_8.name());
        factory.addAdditionalTomcatConnectors(connector);
        boundPort = adminPort;
    }

    /** 管理端口实际监听成功时返回端口号，否则返回 null。 */
    public Integer getBoundPort() {
        return boundPort;
    }

    /** 主服务启动前先探测端口，避免占用时整个进程启动失败。 */
    private boolean available(String address, int port) {
        try (ServerSocket socket = new ServerSocket()) {
            socket.setReuseAddress(false);
            socket.bind(new InetSocketAddress(address, port));
            return true;
        } catch (IOException e) {
            return false;
        }
    }
}
