package com.agentsquad.squad.agent.tool;

import com.agentsquad.squad.mapper.ExecutionScopeMapper;
import com.agentsquad.squad.enums.RuntimeExceptionCode;
import com.agentsquad.squad.exceptions.RuntimeBusinessException;
import com.agentsquad.squad.mapper.WorkerDispatchMapper;
import com.agentsquad.squad.model.bo.SandboxExecuteRequestBO;
import com.agentsquad.squad.model.bo.WorkerExecutionIdentityBO;
import com.agentsquad.squad.service.SandboxExecutionService;
import com.agentsquad.squad.service.WorkerDefinitionService;
import com.agentsquad.squad.utils.RuntimePayloads;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.message.ToolResultBlock;
import io.agentscope.core.session.SessionRecorder;
import io.agentscope.core.tool.AgentTool;
import io.agentscope.core.tool.ToolCallParam;
import jakarta.annotation.Resource;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.List;
import java.util.Map;

/**
 * 受控隔离 Python 工具。
 * <p>
 * 只接收脚本和文本文件；用户、会话与 Worker guard 均由服务端绑定派生，未知执行不能换键重放。
 */
@Component
@ConditionalOnProperty(prefix = "agentsquad.delivery", name = "enabled", havingValue = "true")
public class SandboxPythonTool implements AgentTool {
    @Resource
    private SandboxExecutionService sandbox;

    @Resource
    private ObjectMapper json;

    @Resource
    private ExecutionScopeMapper bindings;

    @Resource
    private WorkerDispatchMapper workers;

    @Resource
    private WorkerDefinitionService definitions;

    @Override
    public String getName() {
        return "sandbox_python";
    }

    @Override
    public String getDescription() {
        return "在无网络、资源受限的隔离沙箱运行Python。输出文件先暂存，需commit_artifact提交才能交付。"
                + "附件必须已关联当前执行，路径为inputs/{artifactId}/{fileName}。同一业务重试复用logicalKey；UNKNOWN表示结果待核对，不得换键重发。";
    }

    @Override
    public Map<String, Object> getParameters() {
        return Map.of("type", "object", "properties", Map.of(
                        "logicalKey", Map.of("type", "string", "minLength", 1, "maxLength", 160),
                        "script", Map.of("type", "string", "minLength", 1, "maxLength", 100000),
                        "files", Map.of("type", "array", "maxItems", 20, "items", Map.of("type", "object",
                                "properties", Map.of("path", Map.of("type", "string"), "content", Map.of("type", "string")),
                                "required", List.of("path", "content"), "additionalProperties", false)),
                        "artifactIds", Map.of("type", "array", "maxItems", 20, "uniqueItems", true, "items", Map.of("type", "string", "format", "uuid")),
                        "outputPaths", Map.of("type", "array", "maxItems", 20, "items", Map.of("type", "string")),
                        "timeoutSeconds", Map.of("type", "integer", "minimum", 1, "maximum", 30)),
                "required", List.of("logicalKey", "script", "files", "outputPaths", "timeoutSeconds"),
                "additionalProperties", false);
    }

    @Override
    public Mono<ToolResultBlock> callAsync(ToolCallParam param) {
        return Mono.fromCallable(() -> {
            RuntimeContext context = param.getRuntimeContext();
            if (context == null || param.getToolUseBlock() == null) {
                return ToolResultBlock.error("缺少固定执行身份");
            }
            try {
                SandboxExecuteRequestBO request = json.convertValue(param.getInput(), SandboxExecuteRequestBO.class);
                var result = sandbox.execute(context.getUserId(), context.getSessionId(), guard(context),
                        context.get(SessionRecorder.TURN_ID_KEY, String.class), param.getToolUseBlock().getId(), request);
                return ToolResultBlock.text(RuntimePayloads.toJson(json, result));
            } catch (RuntimeBusinessException exception) {
                return ToolResultBlock.error(exception.getMessage());
            } catch (IllegalArgumentException exception) {
                return ToolResultBlock.error("沙箱请求结构无效");
            }
        }).subscribeOn(Schedulers.boundedElastic());
    }

    private String guard(RuntimeContext context) {
        String bindingId = bindings.findBindingBySession(context.getUserId(), context.getSessionId());
        var worker = workers.findByBinding(context.getUserId(), bindingId);
        if (worker == null) {
            return null;
        }
        if (!definitions.requirePublished(worker.getWorkerKey(), worker.getWorkerVersion()).toolAllowlist().contains(getName())) {
            throw new RuntimeBusinessException(
                    RuntimeExceptionCode.FORBIDDEN, "该Worker没有沙箱执行权限");
        }
        var identity = context.get(WorkerExecutionIdentityBO.class);
        if (identity == null || !bindingId.equals(identity.bindingId())) {
            throw new RuntimeBusinessException(
                    RuntimeExceptionCode.FORBIDDEN, "缺少本次Worker领取的固定执行身份");
        }
        return identity.guardToken();
    }
}
