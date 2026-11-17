package com.greydrive.common;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

public final class WebRequestInfo {

    private WebRequestInfo() {
    }

    public static String clientIp() {
        HttpServletRequest request = current();
        if (request == null) {
            return null;
        }
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    public static String userAgent() {
        HttpServletRequest request = current();
        if (request == null) {
            return null;
        }
        String ua = request.getHeader("User-Agent");
        return ua != null && ua.length() > 256 ? ua.substring(0, 256) : ua;
    }

    private static HttpServletRequest current() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            return attributes.getRequest();
        }
        return null;
    }
}
