package com.greydrive.bootstrap;

import com.greydrive.config.AdminConnectorConfig;
import com.greydrive.config.GreyDriveProperties;
import com.greydrive.nativebridge.NativeLibrary;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.web.context.WebServerInitializedEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;

@Component
public class StartupInfoLogger implements ApplicationListener<WebServerInitializedEvent> {

    private static final Logger log = LoggerFactory.getLogger(StartupInfoLogger.class);

    private final GreyDriveProperties properties;
    private final ObjectProvider<AdminConnectorConfig> adminConnector;

    public StartupInfoLogger(GreyDriveProperties properties, ObjectProvider<AdminConnectorConfig> adminConnector) {
        this.properties = properties;
        this.adminConnector = adminConnector;
        // Touch the loader early so the startup log reports native acceleration state.
        NativeLibrary.isAvailable();
    }

    @Override
    public void onApplicationEvent(WebServerInitializedEvent event) {
        int port = event.getWebServer().getPort();
        List<String> addresses = localIpv4();
        String ip = addresses.isEmpty() ? "127.0.0.1" : addresses.get(0);
        String dataDir = Paths.get(properties.getDataDir()).toAbsolutePath().normalize().toString();
        AdminConnectorConfig admin = adminConnector.getIfAvailable();
        Integer adminPort = admin == null ? null : admin.getBoundPort();

        StringBuilder sb = new StringBuilder();
        sb.append("\n============================================================\n");
        sb.append(" GreyDrive 已启动\n");
        List<String> apiUrls = new ArrayList<>();
        apiUrls.add(baseUrl("127.0.0.1", port));
        for (String address : addresses) {
            apiUrls.add(baseUrl(address, port));
        }
        sb.append(" 服务/API:   ").append(String.join("  ", apiUrls)).append('\n');
        if (adminPort != null) {
            List<String> adminUrls = new ArrayList<>();
            adminUrls.add(baseUrl("127.0.0.1", adminPort));
            for (String address : addresses) {
                adminUrls.add(baseUrl(address, adminPort));
            }
            sb.append(" 管理后台:   ").append(String.join("  ", adminUrls)).append('\n');
        } else if (properties.getAdmin().isEnabled()) {
            sb.append(" 管理后台:   未启用（端口 ").append(properties.getAdmin().getPort())
                    .append(" 不可用，详见上方告警）\n");
        }
        sb.append(" Swagger:    http://").append(ip).append(':').append(port).append("/swagger-ui/index.html\n");
        sb.append(" OpenAPI:    http://").append(ip).append(':').append(port).append("/v3/api-docs\n");
        sb.append(" 性能加速:   ")
                .append(NativeLibrary.isAvailable() ? "Rust 原生 (" + NativeLibrary.detail() + ")" : "Java 回退")
                .append('\n');
        sb.append(" 工作目录:   ").append(Paths.get("").toAbsolutePath().normalize()).append('\n');
        sb.append(" 数据目录:   ").append(dataDir).append('\n');
        sb.append(" 日志文件:   ").append(dataDir).append("/logs/greydrive.log\n");
        sb.append("============================================================");
        log.info(sb.toString());
    }

    /** 80 端口省略 :80，与浏览器输入习惯一致 */
    private String baseUrl(String host, int port) {
        return port == 80 ? "http://" + host + "/" : "http://" + host + ":" + port + "/";
    }

    private List<String> localIpv4() {
        List<String> result = new ArrayList<>();
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces != null && interfaces.hasMoreElements()) {
                NetworkInterface ni = interfaces.nextElement();
                if (!ni.isUp() || ni.isLoopback() || ni.isVirtual()) {
                    continue;
                }
                Enumeration<InetAddress> addresses = ni.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress address = addresses.nextElement();
                    if (address instanceof Inet4Address && !address.isLoopbackAddress()) {
                        result.add(address.getHostAddress());
                    }
                }
            }
        } catch (Exception ignored) {
            // 获取失败时仅打印本地回环地址
        }
        return result;
    }
}
