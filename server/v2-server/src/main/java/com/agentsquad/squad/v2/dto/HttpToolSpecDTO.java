package com.agentsquad.squad.v2.dto;

import com.agentsquad.squad.v2.enums.HttpToolMethod;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * HTTP 工具请求模板
 * <p>
 * 保存时不强制地址必填，由服务端自动判定就绪度；试运行与发布时要求请求方法与地址完整合法。
 */
@Data
public class HttpToolSpecDTO {
    /**
     * 请求方法
     */
    private HttpToolMethod method = HttpToolMethod.GET;

    /**
     * 地址模板，路径与查询可使用参数及凭据占位符
     */
    @Size(max = 2048)
    private String url = "";

    /**
     * 请求头模板
     */
    @NotNull
    @Valid
    @Size(max = 20)
    private List<@NotNull KeyValueDTO> headers = List.of();

    /**
     * JSON 请求体模板，仅 POST、PUT、PATCH 使用
     */
    @Size(max = 20000)
    private String bodyTemplate;

    /**
     * 凭据引用名
     */
    @Size(max = 80)
    private String credentialRef;

    /**
     * 超时毫秒数
     */
    @NotNull
    @Min(1000)
    @Max(60000)
    private Integer timeoutMs = 15000;

    /**
     * 结果提取的 JSON Pointer
     */
    @Size(max = 500)
    private String resultPointer;
}
