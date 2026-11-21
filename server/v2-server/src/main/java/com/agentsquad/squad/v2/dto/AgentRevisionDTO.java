package com.agentsquad.squad.v2.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Data;

/**
 * 版本操作条件请求
 * <p>
 * 描述管理端提交的配置或操作条件。
 */
@Data
public class AgentRevisionDTO {
    /**
     * 目标记录的预期修订号
     */
    @NotNull
    @PositiveOrZero
    private Long revision;
}
