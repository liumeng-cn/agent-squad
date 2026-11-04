package com.agentsquad.squad.common.web;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 统一 HTTP 响应包装类。
 *
 * @param <T> 数据载荷类型
 */
@Data
@NoArgsConstructor(access = AccessLevel.PRIVATE)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class ApiResult<T> {

    public static final String SUCCESS_CODE = "0";

    private String code;
    private T data;
    private String message;

    /**
     * 构造成功响应。
     */
    public static <T> ApiResult<T> success(T data) {
        return new ApiResult<>(SUCCESS_CODE, data, null);
    }

    /**
     * 构造错误响应。
     */
    public static <T> ApiResult<T> error(String code, String message) {
        return new ApiResult<>(code, null, message);
    }
}
