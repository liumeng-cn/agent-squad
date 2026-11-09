package com.agentsquad.squad.enums;

import lombok.Getter;

/**
 * 统一响应的业务码及默认提示。
 *
 * <p>成功码固定为 0；现有错误码数值保持不变，前端通过响应体 code 判断业务结果，不依赖 HTTP 状态。
 * 需要更具体的提示时，可在构造错误响应时覆盖默认 msg。</p>
 */
@Getter
public enum ApiCode {

    SUCCESS(0, "成功", "SUCCESS", "成功"),
    BAD_REQUEST(400, "请求参数错误", "BAD_REQUEST", "请求参数错误"),
    UNAUTHORIZED(401, "需要登录后继续操作", "UNAUTHORIZED", "需要登录"),
    FORBIDDEN(403, "无权执行该操作", "FORBIDDEN", "无权操作"),
    NOT_FOUND(404, "资源不存在", "NOT_FOUND", "资源不存在"),
    METHOD_NOT_ALLOWED(405, "请求方法不支持", "METHOD_NOT_ALLOWED", "请求方法不支持"),
    CONFLICT(409, "资源状态已变更，请刷新后重试", "CONFLICT", "资源状态冲突"),
    GONE(410, "资源已过期", "GONE", "资源已过期"),
    UNSUPPORTED_MEDIA_TYPE(415, "不支持的请求格式", "UNSUPPORTED_MEDIA_TYPE", "不支持的请求格式"),
    VALIDATION_FAILED(422, "参数校验失败", "VALIDATION_FAILED", "参数校验失败"),
    TOO_MANY_REQUESTS(429, "请求过于频繁，请稍后重试", "TOO_MANY_REQUESTS", "请求过于频繁"),
    INTERNAL_ERROR(500, "服务暂时不可用", "INTERNAL_ERROR", "服务内部错误"),
    SERVICE_UNAVAILABLE(503, "服务暂时不可用", "SERVICE_UNAVAILABLE", "服务不可用");

    /** 业务编码 */
    private final int code;
    /** 默认用户提示 */
    private final String msg;
    /** 英文名 */
    private final String name;
    /** 中文描述 */
    private final String desc;

    ApiCode(int code, String msg, String name, String desc) {
        this.code = code;
        this.msg = msg;
        this.name = name;
        this.desc = desc;
    }

    /**
     * 按码值查找业务码；未登记的状态码按客户端或服务端错误归类。
     */
    public static ApiCode fromCode(int code) {
        for (ApiCode apiCode : values()) {
            if (apiCode.code == code) {
                return apiCode;
            }
        }
        return code >= INTERNAL_ERROR.code ? INTERNAL_ERROR : BAD_REQUEST;
    }
}
