package com.greydrive.common;

public record ApiResponse<T>(int code, String message, T data, String traceId) {

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(0, "success", data, TraceIdFilter.current());
    }

    public static ApiResponse<Void> ok() {
        return new ApiResponse<>(0, "success", null, TraceIdFilter.current());
    }

    public static <T> ApiResponse<T> error(ErrorCode errorCode) {
        return new ApiResponse<>(errorCode.getCode(), errorCode.getMessage(), null, TraceIdFilter.current());
    }

    public static <T> ApiResponse<T> error(ErrorCode errorCode, String message) {
        return new ApiResponse<>(errorCode.getCode(), message, null, TraceIdFilter.current());
    }

    public static <T> ApiResponse<T> error(ErrorCode errorCode, String message, T data) {
        return new ApiResponse<>(errorCode.getCode(), message, data, TraceIdFilter.current());
    }
}
