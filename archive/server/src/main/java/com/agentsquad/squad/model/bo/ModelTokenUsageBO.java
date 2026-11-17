package com.agentsquad.squad.model.bo;

/**
 * 模型用量证据
 * <p>
 * 保留供应商已报告的计数，不累加流式累计块；缓存及推理计数是分项，不重复加入总数。
 */
public record ModelTokenUsageBO(long inputTokens, long outputTokens, long cachedTokens,
                                long cacheCreationTokens, long reasoningTokens, long toolUsePromptTokens) {
}
