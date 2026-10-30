package com.agentsquad.squad.skill.dto;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * 用户技能导入请求。
 * <p>
 * 归属由认证主体确定；文件集合必须通过包校验后才可发布。
 */
public record SkillImportRequest(@JsonProperty(value = "files", required = true) List<SkillFile> files) {
    /**
     * 传输边界拒绝模型或客户端附加身份、链接及未声明字段。
     */
    @JsonAnySetter
    public void rejectUnknownField(String name, Object value) {
        throw new IllegalArgumentException("技能请求包含不支持的字段");
    }
}
