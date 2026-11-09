package com.agentsquad.squad.enums;

import lombok.Getter;

/**
 * 模型目录声明的业务能力
 * <p>
 * 为用户筛选和Worker选择提供共享标签，不表示模型质量评分或已通过自动评测。
 */
@Getter
public enum ModelCapability {
    REASONING(1, "REASONING", "推理"),
    TEXT_GENERATION(2, "TEXT_GENERATION", "文本生成"),
    CODING(3, "CODING", "编码"),
    TOOL_USE(4, "TOOL_USE", "工具使用"),
    VISION(5, "VISION", "视觉");

    /**
     * 编码
     */
    private final int code;
    /**
     * 英文名
     */
    private final String name;
    /**
     * 中文描述
     */
    private final String desc;

    ModelCapability(int code, String name, String desc) {
        this.code = code;
        this.name = name;
        this.desc = desc;
    }
}
