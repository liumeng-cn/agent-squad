package com.agentsquad.squad.v2.dto;

import com.agentsquad.squad.v2.enums.ToolType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.Map;

/**
 * 工具试运行请求
 * <p>
 * 提交尚未保存的工具定义和一次调用参数，服务端按运行时规则生成工具并调用，不写入数据。
 */
@Data
public class ToolTestRunDTO {
    /**
     * 工具类型
     */
    @NotNull
    private ToolType toolType;

    /**
     * 待试运行的工具定义
     */
    @NotNull
    @Valid
    private ToolDraftDTO draft;

    /**
     * 本次调用参数
     */
    @NotNull
    private Map<String, Object> arguments = Map.of();
}
