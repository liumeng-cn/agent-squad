package com.agentsquad.squad.domain.dto;

/**
 * 回复状态变更增量
 * <p>
 * 状态、版本及允许操作均由服务端提交事实生成，不要求页面自行猜测恢复或取消资格。
 */
public record ExecutionChangedPayload(int schemaVersion, ResponseExecutionView execution) {
}
