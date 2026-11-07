package com.agentsquad.squad.integration.agentscope.inbox;

import com.agentsquad.squad.integration.agentscope.inbox.service.RuntimeEventReceiptService;

import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

import java.util.Objects;

/**
 * 运行集成模块的绑定收件器工厂
 * <p>
 * 独立于收件服务装配适配器，确保回调持有Spring事务代理，不通过服务自身this绕过事务。
 */
@Component
public class RuntimeEventSinkFactory {
    @Resource
    private RuntimeEventReceiptService receipts;

    public RuntimeEventSink bind(RuntimeEventSource source) {
        return new RuntimeEventSink(receipts, Objects.requireNonNull(source));
    }
}
