package com.agentsquad.squad.model.bo;

import com.agentsquad.squad.model.vo.ResponseExecutionVO;

/**
 * 回复状态变更增量
 * <p>
 * 状态、版本及允许操作均由服务端提交事实生成，不要求页面自行猜测恢复或取消资格。
 */
public record ExecutionChangedPayloadBO(int schemaVersion, ResponseExecutionVO execution) {
}
