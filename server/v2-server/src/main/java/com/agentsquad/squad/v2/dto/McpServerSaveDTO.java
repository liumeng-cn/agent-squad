package com.agentsquad.squad.v2.dto;

import com.agentsquad.squad.v2.enums.McpTransport;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * MCP 服务保存请求
 * <p>
 * 新增与编辑统一提交，编码由系统自动生成；保存时不强制连接地址必填，由服务端自动判定就绪状态。
 */
@Data
public class McpServerSaveDTO {
    /**
     * 编辑已有服务时的预期目录修订号，新增时为空
     */
    @PositiveOrZero
    private Long revision;

    /**
     * 后台展示名称
     */
    @NotBlank
    @Size(max = 120)
    private String serverName;

    /**
     * 服务用途说明
     */
    @Size(max = 500)
    private String description = "";

    /**
     * 传输方式
     */
    private McpTransport transportType = McpTransport.STREAMABLE_HTTP;

    /**
     * 远程连接地址
     */
    @Size(max = 2048)
    private String endpoint = "";

    /**
     * 凭据引用名
     */
    @Size(max = 80)
    private String credentialRef;

    /**
     * 附加请求头，值可写凭据占位符
     */
    @NotNull
    @Valid
    @Size(max = 20)
    private List<@NotNull KeyValueDTO> headers = List.of();

    /**
     * 连接与调用超时毫秒数
     */
    @NotNull
    @Min(1000)
    @Max(120000)
    private Integer timeoutMs = 30000;
}
