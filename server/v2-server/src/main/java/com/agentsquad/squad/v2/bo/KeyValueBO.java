package com.agentsquad.squad.v2.bo;

import lombok.Data;

/**
 * 键值对
 * <p>
 * 保存在 JSONB 字段中的请求头等成对配置，也用于运行时渲染。
 */
@Data
public class KeyValueBO {
    /**
     * 名称
     */
    private String name;

    /**
     * 取值，可包含模板占位符
     */
    private String value;
}
