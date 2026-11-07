package com.agentsquad.squad.integration.agentscope;

import com.agentsquad.squad.domain.entity.DeliveryDO;
import com.agentsquad.squad.domain.entity.ProjectionTargetDO;
import io.agentscope.core.session.SessionEvent;

import java.util.List;

/**
 * 平台命令到原生会话的窄接口；实现必须使用原命令 ID 做原生幂等键。
 */
public interface NativeSessionGateway {

    NativeSubmissionReceipt submit(DeliveryDO delivery, String message);

    /**
     * 中断绑定会话的当前原生运行；返回 false 表示不存在活跃运行。
     */
    boolean stop(DeliveryDO delivery);

    /** 只读检查原生写入者与运行，不构建业务 Worker 或启动调度；未知时保守不收尾。 */
    default boolean quiescent(ProjectionTargetDO target) {
        return false;
    }

    /**
     * 恢复绑定会话的指定原生回合；幂等，已排队或已结束的恢复由 SDK 裁决。
     */
    void resume(DeliveryDO delivery, String turnId);

    /**
     * 把用户答复注入挂起的原生外部执行请求；返回原生受理状态，
     * 请求已不存在等终局拒绝由实现以 IllegalStateException 之外的方式表达。
     */
    String respond(DeliveryDO delivery, String requestId, String answerText);

    List<NativeEventView> readEvents(ProjectionTargetDO target, long after, int limit);

    /** 内部收件读取完整已提交信封，只在内存校验，不写原始载荷到平台表。 */
    List<SessionEvent> readCommittedEvents(ProjectionTargetDO target, long after, int limit);

    /** 后台恢复明确启动已获授权绑定的待执行调度；查询入口不得隐式调用。 */
    void startPending(ProjectionTargetDO target);

    /**
     * 读取原命令的 SDK 持久收据，用于区分仍在运行与 writer 租约失效的未完成回合。
     */
    NativeSubmissionReceipt inspectTask(ProjectionTargetDO target, String commandId);
}
