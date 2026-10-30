package com.agentsquad.squad.runtime.domain;

/**
 * 当前进程领取的Worker执行身份。
 * <p>
 * 固定创建原生Agent时取得的guard；工具不得从数据库读取新guard代替旧调用方继续执行。
 */
public record WorkerExecutionIdentity(String bindingId, String guardToken) {
}
