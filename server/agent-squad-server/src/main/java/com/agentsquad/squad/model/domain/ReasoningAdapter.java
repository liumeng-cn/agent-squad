package com.agentsquad.squad.model.domain;

/**
 * 模型思考参数的协议适配方式
 * <p>
 * 同一页面选择通过明确协议映射，不向不支持的供应商发送推理参数。
 */
public enum ReasoningAdapter {
    NONE, SPARK_THINKING, OPENAI_EFFORT
}
