package com.agentsquad.squad.integration.agentscope;

import com.agentsquad.squad.conversation.domain.DeliveryRow;
import com.agentsquad.squad.runtime.domain.ProjectionTarget;

import java.util.List;

/**
 * 平台命令到原生会话的窄接口；实现必须使用原命令 ID 做原生幂等键。
 */
public interface NativeSessionGateway {

    NativeSubmissionReceipt submit(DeliveryRow delivery, String message);

    /**
     * 中断绑定会话的当前原生运行；返回 false 表示不存在活跃运行。
     */
    boolean stop(DeliveryRow delivery);

    /**
     * 恢复绑定会话的指定原生回合；幂等，已排队或已结束的恢复由 SDK 裁决。
     */
    void resume(DeliveryRow delivery, String turnId);

    /**
     * 把用户答复注入挂起的原生外部执行请求；返回原生受理状态，
     * 请求已不存在等终局拒绝由实现以 IllegalStateException 之外的方式表达。
     */
    String respond(DeliveryRow delivery, String requestId, String answerText);

    List<NativeEventView> readEvents(ProjectionTarget target, long after, int limit);
}
