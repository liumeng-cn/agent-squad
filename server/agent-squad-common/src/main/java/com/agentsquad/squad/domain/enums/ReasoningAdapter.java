package com.agentsquad.squad.domain.enums;

import lombok.Getter;

/**
 * 模型思考参数的协议适配方式
 * <p>
 * 同一页面选择通过明确协议映射，不向不支持的供应商发送推理参数。
 */
@Getter
public enum ReasoningAdapter {
    NONE(1, "NONE", "无适配"),
    SPARK_THINKING(2, "SPARK_THINKING", "星火思考"),
    QWEN_THINKING(3, "QWEN_THINKING", "通义思考"),
    OPENAI_EFFORT(4, "OPENAI_EFFORT", "OpenAI 推理强度");

    /** 编码 */
    private final int code;
    /** 英文名 */
    private final String name;
    /** 中文描述 */
    private final String desc;

    ReasoningAdapter(int code, String name, String desc) {
        this.code = code;
        this.name = name;
        this.desc = desc;
    }
}
