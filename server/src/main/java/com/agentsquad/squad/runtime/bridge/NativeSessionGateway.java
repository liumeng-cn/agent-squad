package com.agentsquad.squad.runtime.bridge;

import com.agentsquad.squad.runtime.domain.DeliveryRow;
import com.agentsquad.squad.runtime.domain.ProjectionTarget;

import java.util.List;

/**
 * 平台命令到原生会话的窄接口；实现必须使用原命令 ID 做原生幂等键。
 */
public interface NativeSessionGateway {

    NativeSubmissionReceipt submit(DeliveryRow delivery, String message);

    List<NativeEventView> readEvents(ProjectionTarget target, long after, int limit);
}
