package com.agentsquad.squad.interaction.dto;

import jakarta.validation.constraints.NotBlank;

import java.util.Map;

/**
 * 追问或审批的类型化答复。
 * <p>
 * 服务层按持久交互类型核验字段，审批确认摘要不能由浏览器编辑。
 */
public record InteractionAnswer(@NotBlank String kind, Boolean approve, String confirmedRequestHash,
                                Map<String, Object> value) {
}
