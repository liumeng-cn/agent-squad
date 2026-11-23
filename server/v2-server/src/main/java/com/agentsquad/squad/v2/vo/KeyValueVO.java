package com.agentsquad.squad.v2.vo;

import lombok.Data;

/**
 * 键值对视图
 * <p>
 * 返回请求头等成对配置，值中保留模板占位符，不包含凭据值。
 */
@Data
public class KeyValueVO {
    /**
     * 名称
     */
    private String name;

    /**
     * 取值
     */
    private String value;
}
