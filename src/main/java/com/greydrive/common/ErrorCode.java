package com.greydrive.common;

import lombok.Getter;

@Getter
public enum ErrorCode {

    OK(0, 200, "success"),

    INVALID_PARAM(10001, 400, "参数校验失败"),
    UNAUTHORIZED(10002, 401, "未登录或令牌无效"),
    FORBIDDEN(10003, 403, "无权限"),
    NOT_FOUND(10004, 404, "资源不存在或无权访问"),
    CONFLICT(10005, 409, "资源冲突"),
    TOO_LARGE(10006, 413, "超过大小限制"),
    UNSUPPORTED_MEDIA(10007, 415, "不支持的媒体类型"),
    RANGE_NOT_SATISFIABLE(10008, 416, "Range 不满足"),
    RATE_LIMITED(10009, 429, "请求过于频繁"),
    INTERNAL(10010, 500, "内部错误"),

    USERNAME_EXISTS(11001, 409, "用户名已存在"),
    BAD_CREDENTIALS(11002, 401, "用户名或密码错误"),
    TOKEN_INVALID(11003, 401, "令牌过期或无效"),
    MFA_CODE_INVALID(11004, 401, "二次验证码错误"),
    MFA_TOKEN_INVALID(11005, 401, "二次验证令牌无效或已过期"),
    MFA_ALREADY_ENABLED(11006, 409, "二次验证已启用"),

    FOLDER_NOT_FOUND(12001, 404, "目录不存在"),
    FILE_NOT_FOUND(12002, 404, "文件不存在"),
    NAME_CONFLICT(12003, 409, "同级名称已存在"),
    INVALID_MOVE(12005, 400, "非法移动"),

    UPLOAD_NOT_FOUND(13001, 404, "上传会话不存在或已过期"),
    CHUNK_INVALID(13002, 400, "分片序号非法"),
    CHUNK_MISSING(13003, 409, "缺少分片"),
    SIZE_MISMATCH(13004, 400, "大小或哈希与声明不一致"),

    SHARE_NOT_FOUND(14001, 404, "分享不存在"),
    SHARE_EXPIRED(14002, 410, "分享已过期"),
    SHARE_CODE_INVALID(14003, 403, "提取码错误或缺失"),
    SHARE_PERMISSION_DENIED(14004, 403, "超出分享权限"),

    VERSION_NOT_FOUND(15001, 404, "版本不存在"),

    TAG_EXISTS(16001, 409, "标签已存在"),

    STORAGE_ERROR(17001, 500, "存储读写失败"),
    PREVIEW_UNSUPPORTED(18001, 415, "不支持该类型预览");

    private final int code;
    private final int httpStatus;
    private final String message;

    ErrorCode(int code, int httpStatus, String message) {
        this.code = code;
        this.httpStatus = httpStatus;
        this.message = message;
    }
}
