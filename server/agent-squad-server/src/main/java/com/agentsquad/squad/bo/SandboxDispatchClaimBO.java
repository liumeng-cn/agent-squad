package com.agentsquad.squad.bo;

import com.agentsquad.squad.po.SandboxRunPO;

/**
 * 沙箱事务受理结果。
 * <p>
 * 只有同事务首次创建记录的调用取得派发权；重启和并发重放不能从IN_FLIGHT再次派发。
 */
public record SandboxDispatchClaimBO(SandboxRunPO run, boolean newlyAccepted) {
}
