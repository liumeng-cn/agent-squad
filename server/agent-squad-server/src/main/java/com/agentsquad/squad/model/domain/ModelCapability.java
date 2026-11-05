package com.agentsquad.squad.model.domain;

/**
 * 模型目录声明的业务能力
 * <p>
 * 为用户筛选和Worker选择提供共享标签，不表示模型质量评分或已通过自动评测。
 */
public enum ModelCapability {
    REASONING, TEXT_GENERATION, CODING, TOOL_USE, VISION
}
