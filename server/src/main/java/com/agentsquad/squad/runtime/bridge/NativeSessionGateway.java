package com.agentsquad.squad.runtime.bridge;

import com.agentsquad.squad.runtime.domain.DeliveryRow;

/** 平台命令到原生会话的窄接口；实现必须使用原命令 ID 做原生幂等键。 */
public interface NativeSessionGateway {

    NativeSubmissionReceipt submit(DeliveryRow delivery, String message);
}
