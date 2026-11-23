package com.agentsquad.squad.v2.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Data;

/**
 * 配置操作条件请求
 * <p>
 * 发布、启停和切换版本时携带的预期修订号，修订号不一致时返回冲突。
 */
@Data
public class ConfigRevisionDTO {
    /**
     * 目标记录的预期修订号
     */
    @NotNull
    @PositiveOrZero
    private Long revision;
}
