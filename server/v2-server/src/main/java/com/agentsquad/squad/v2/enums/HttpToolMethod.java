package com.agentsquad.squad.v2.enums;

import lombok.Getter;

/**
 * HTTP 工具请求方法
 * <p>
 * POST、PUT、PATCH 可携带 JSON 请求体，GET、DELETE 把未使用的参数放入查询串。
 */
@Getter
public enum HttpToolMethod {
    GET("查询", false),
    POST("提交", true),
    PUT("整体更新", true),
    PATCH("部分更新", true),
    DELETE("删除", false);

    private final String desc;

    private final boolean bodyAllowed;

    HttpToolMethod(String desc, boolean bodyAllowed) {
        this.desc = desc;
        this.bodyAllowed = bodyAllowed;
    }
}
