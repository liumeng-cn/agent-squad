package com.agentsquad.squad.integration.agentscope.inbox;

import io.agentscope.core.session.SessionEvent;
import io.agentscope.core.session.SessionExportSink;

/**
 * 固定身份的官方日志导出适配器
 * <p>
 * 每个运行上下文持有自己的绑定，委托Spring事务代理完成落库后才返回SDK；不自行吞掉失败。
 */
public final class RuntimeEventSink implements SessionExportSink {
    private final RuntimeEventReceiptService receipts;
    private final RuntimeEventSource source;

    RuntimeEventSink(RuntimeEventReceiptService receipts, RuntimeEventSource source) {
        this.receipts = receipts;
        this.source = source;
    }

    @Override
    public String name() {
        return RuntimeEventReceiptService.SINK_NAME;
    }

    @Override
    public void accept(SessionEvent event) {
        receipts.receive(source, event);
    }
}
