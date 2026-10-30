package com.agentsquad.squad.skill.dto;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;


/**
 * 技能使用授权修改请求。
 * <p>
 * expectedVersion 防止管理页面覆盖另一笔启停操作。
 */
public record SkillGrantRequest(@NotNull @JsonProperty(value = "enabled", required = true) Boolean enabled,
                                @NotNull @JsonProperty(value = "expectedVersion", required = true) Long expectedVersion) {
    /**
     * 传输边界拒绝模型或客户端附加身份、链接及未声明字段。
     */
    @JsonAnySetter
    public void rejectUnknownField(String name, Object value) {
        throw new IllegalArgumentException("技能请求包含不支持的字段");
    }
}
