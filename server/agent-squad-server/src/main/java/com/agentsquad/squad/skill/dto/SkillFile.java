package com.agentsquad.squad.skill.dto;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonProperty;


/**
 * 技能包中的只读文本文件。
 * <p>
 * 导入协议仅接受路径与文本，不接受本机文件句柄或符号链接。
 */
public record SkillFile(@JsonProperty(value = "path", required = true) String path,
                        @JsonProperty(value = "content", required = true) String content) {
    /**
     * 传输边界拒绝模型或客户端附加身份、链接及未声明字段。
     */
    @JsonAnySetter
    public void rejectUnknownField(String name, Object value) {
        throw new IllegalArgumentException("技能请求包含不支持的字段");
    }
}
