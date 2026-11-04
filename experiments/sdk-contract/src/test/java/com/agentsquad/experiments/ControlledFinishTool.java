package com.agentsquad.experiments;

import io.agentscope.core.message.ToolResultBlock;
import io.agentscope.core.permission.PermissionContextState;
import io.agentscope.core.permission.PermissionDecision;
import io.agentscope.core.tool.ToolBase;
import io.agentscope.core.tool.ToolCallParam;
import io.agentscope.core.tool.ToolSuspendException;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 可控制暂停边界的终结工具
 * <p>
 * 仅用内存门闩制造确定性的输入竞争，所有结果为固定实验文本，无外部业务副作用。
 */
final class ControlledFinishTool extends ToolBase {
    private final ControlledFinishMode mode;
    private final AtomicInteger calls = new AtomicInteger();
    private final CountDownLatch entered = new CountDownLatch(1);
    private final CountDownLatch release = new CountDownLatch(1);

    ControlledFinishTool(ControlledFinishMode mode) {
        this("finish", mode);
    }

    ControlledFinishTool(String name, ControlledFinishMode mode) {
        super(ToolBase.builder().name(name).description("受控终结实验")
                .inputSchema(Map.of("type", "object", "properties", Map.of()))
                .returnDirect(true).readOnly(false).externalTool(mode == ControlledFinishMode.EXTERNAL));
        this.mode = mode;
    }

    @Override
    public Mono<PermissionDecision> checkPermissions(Map<String, Object> input, PermissionContextState context) {
        return Mono.just(mode == ControlledFinishMode.APPROVAL
                ? PermissionDecision.ask("本地审批实验") : PermissionDecision.allow("本地实验允许"));
    }

    @Override
    public Mono<ToolResultBlock> callAsync(ToolCallParam param) {
        return Mono.fromCallable(() -> {
            calls.incrementAndGet();
            entered.countDown();
            if (mode == ControlledFinishMode.SUSPEND) {
                throw new ToolSuspendException("等待本地实验结果");
            }
            if (mode == ControlledFinishMode.BLOCKED && !release.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("实验门闩超时");
            }
            return ToolResultBlock.text("终结工具实验结果");
        });
    }

    boolean awaitEntered() throws InterruptedException {
        return entered.await(10, TimeUnit.SECONDS);
    }

    void release() {
        release.countDown();
    }

    int calls() {
        return calls.get();
    }
}
