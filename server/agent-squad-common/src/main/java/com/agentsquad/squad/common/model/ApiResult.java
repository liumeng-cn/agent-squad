package com.agentsquad.squad.common.model;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 统一 HTTP 响应包装类。
 *
 * <p>code 使用 {@link ApiCode} 的码值，成功固定为 0；错误码与 HTTP 状态码一致。
 * data 承载业务载荷，msg 为可读提示。</p>
 *
 * @param <T> 数据载荷类型
 */
@Data
@NoArgsConstructor(access = AccessLevel.PRIVATE)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class ApiResult<T> {

    private int code;
    private T data;
    private String msg;

    /**
     * 构造成功响应。
     */
    public static <T> ApiResult<T> success(T data) {
        return new ApiResult<>(ApiCode.SUCCESS.getCode(), data, ApiCode.SUCCESS.getMsg());
    }

    /**
     * 构造带默认提示的错误响应。
     */
    public static <T> ApiResult<T> error(ApiCode apiCode) {
        return new ApiResult<>(apiCode.getCode(), null, apiCode.getMsg());
    }

    /**
     * 构造错误响应；提示为空时回退到业务码默认提示。
     */
    public static <T> ApiResult<T> error(ApiCode apiCode, String msg) {
        String resolved = msg == null || msg.isBlank() ? apiCode.getMsg() : msg;
        return new ApiResult<>(apiCode.getCode(), null, resolved);
    }
}
