package com.agentsquad.squad.model.domain;

/**
 * 一次回复受理时固定的模型选择
 * <p>
 * 只持久Profile引用、非敏感配置指纹和思考模式，不存密钥；后续页面切换不能改变正在执行的回复。
 */
public record AcceptedModelChoice(String modelId, String configurationVersion, ReasoningMode reasoningMode) {
}
