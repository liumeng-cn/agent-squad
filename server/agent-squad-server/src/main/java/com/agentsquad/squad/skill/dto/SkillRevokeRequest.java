package com.agentsquad.squad.skill.dto;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;


/**
 * 技能安全撤销请求。
 * <p>
 * 撤销不可逆，使用独立版本检查避免误覆盖新决定。
 */
public record SkillRevokeRequest(
        @NotNull @JsonProperty(value = "expectedVersion", required = true) Long expectedVersion) {
    /**
     * 传输边界拒绝模型或客户端附加身份、链接及未声明字段。
     */
    @JsonAnySetter
    public void rejectUnknownField(String name, Object value) {
        throw new IllegalArgumentException("技能请求包含不支持的字段");
    }
}
