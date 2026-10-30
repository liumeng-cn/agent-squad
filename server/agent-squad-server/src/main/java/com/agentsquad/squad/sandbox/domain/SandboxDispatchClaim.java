package com.agentsquad.squad.sandbox.domain;

/**
 * 沙箱事务受理结果。
 * <p>
 * 只有同事务首次创建记录的调用取得派发权；重启和并发重放不能从IN_FLIGHT再次派发。
 */
public record SandboxDispatchClaim(SandboxRunRow run, boolean newlyAccepted) {
}
