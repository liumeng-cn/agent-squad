package com.agentsquad.squad.v2.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Master 发布确认请求
 * <p>
 * 携带预检返回的依赖摘要指纹与幂等操作标识，确保确认期间任一依赖发生变化时拒绝静默覆盖。
 */
@Data
public class AgentPublishDTO {
    /**
     * 预期 Master 目录修订号
     */
    @NotNull
    @PositiveOrZero
    private Long revision;

    /**
     * 发布预检返回的全链路依赖版本指纹
     */
    @NotBlank
    @Size(max = 64)
    private String dependencyDigest;

    /**
     * 前端生成的发布幂等操作标识
     */
    @NotBlank
    @Size(max = 64)
    private String publishOperationId;
}
