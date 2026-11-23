package com.agentsquad.squad.v2.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 键值对请求
 * <p>
 * 用于提交 HTTP 请求头等名称与取值成对的配置，值中可使用模板占位符。
 */
@Data
public class KeyValueDTO {
    /**
     * 名称
     */
    @NotBlank
    @Size(max = 100)
    private String name;

    /**
     * 取值
     */
    @NotNull
    @Size(max = 2000)
    private String value;
}
