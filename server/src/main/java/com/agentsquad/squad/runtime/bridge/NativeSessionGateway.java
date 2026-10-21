package com.agentsquad.squad.runtime.bridge;

import com.agentsquad.squad.runtime.domain.DeliveryRow;
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

    List<NativeEventView> readEvents(ProjectionTarget target, long after, int limit);
}
