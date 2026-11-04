package com.agentsquad.squad.common.model;

import lombok.Getter;

/**
 * 统一响应的业务码及默认提示。
 *
 * <p>成功码固定为 0；错误码沿用对应 HTTP 状态码，便于前端与日志按状态分类处理。
 * 需要更具体的提示时，可在构造错误响应时覆盖默认 msg。</p>
 */
@Getter
public enum ApiCode {

    SUCCESS(0, "成功"),
    BAD_REQUEST(400, "请求参数错误"),
    UNAUTHORIZED(401, "需要登录后继续操作"),
    FORBIDDEN(403, "无权执行该操作"),
    NOT_FOUND(404, "资源不存在"),
    METHOD_NOT_ALLOWED(405, "请求方法不支持"),
    CONFLICT(409, "资源状态已变更，请刷新后重试"),
    GONE(410, "资源已过期"),
    UNSUPPORTED_MEDIA_TYPE(415, "不支持的请求格式"),
    VALIDATION_FAILED(422, "参数校验失败"),
    TOO_MANY_REQUESTS(429, "请求过于频繁，请稍后重试"),
    INTERNAL_ERROR(500, "服务暂时不可用"),
    SERVICE_UNAVAILABLE(503, "服务暂时不可用");

    private final int code;
    private final String msg;

    ApiCode(int code, String msg) {
        this.code = code;
        this.msg = msg;
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
