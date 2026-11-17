package com.agentsquad.squad.runtime;

/**
 * 独立回复执行测试身份。
 * <p>
 * 只代表测试数据库内的真实归属行，不代表SDK已经接受或执行了回合。
 */
record ScopeTestExecution(Long userId, Long conversationId, Long executionId,
                          Long scopeId, Long bindingId, String sessionId, Long accountId) {
}
