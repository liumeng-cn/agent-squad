package com.agentsquad.squad.agent.tool;

import com.agentsquad.squad.artifact.service.ArtifactDeliveryService;
import com.agentsquad.squad.common.exception.RuntimeBusinessException;
import com.agentsquad.squad.common.exception.RuntimeExceptionCode;
import com.agentsquad.squad.common.util.RuntimePayloads;
import com.agentsquad.squad.conversation.repository.ExecutionScopeMapper;
import com.agentsquad.squad.runtime.repository.WorkerDispatchMapper;
import com.agentsquad.squad.worker.WorkerDefinitionService;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.agentscope.core.tool.AgentTool;
import io.agentscope.core.tool.ToolCallParam;
import io.agentscope.core.message.ToolResultBlock;
import jakarta.annotation.Resource;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.List;
import java.util.Map;

/**
 * 受控产物发布工具。
 * <p>
 * 将当前执行暂存文件提交为可下载产物，服务层复验摘要、授权和guard；模型不能自报归属。
 */
@Component
@ConditionalOnProperty(prefix = "agentsquad.delivery", name = "enabled", havingValue = "true")
public class CommitArtifactTool implements AgentTool {
    @Resource
    private ArtifactDeliveryService delivery;

    @Resource
    private ExecutionScopeMapper bindings;

    @Resource
    private WorkerDispatchMapper workers;

    @Resource
    private WorkerDefinitionService definitions;

    @Resource
    private ObjectMapper json;

    @Override
    public String getName() {
        return "commit_artifact";
    }

    @Override
    public String getDescription() {
        return "提交当前执行的暂存产物，必须匹配stageId与sha256摘要。成功后返回可交付的artifactId。";
    }

    @Override
    public Map<String, Object> getParameters() {
        return Map.of("type", "object", "properties", Map.of(
                        "stageId", Map.of("type", "string", "format", "uuid"),
                        "expectedHash", Map.of("type", "string", "pattern", "^[0-9a-f]{64}$")),
                "required", List.of("stageId", "expectedHash"), "additionalProperties", false);
    }

    @Override
    public Mono<ToolResultBlock> callAsync(ToolCallParam param) {
        return Mono.fromCallable(() -> {
            var context = param.getRuntimeContext();
            if (context == null) {
                return ToolResultBlock.error("缺少固定执行身份");
            }
            try {
                ArtifactCommitRequest request = json.convertValue(param.getInput(), ArtifactCommitRequest.class);
                String bindingId = bindings.findBindingBySession(context.getUserId(), context.getSessionId());
                var worker = workers.findByBinding(context.getUserId(), bindingId);
                String guard = null;
                if (worker != null) {
                    if (!definitions.requirePublished(worker.workerKey(), worker.workerVersion()).toolAllowlist().contains(getName())) {
                        throw new RuntimeBusinessException(RuntimeExceptionCode.FORBIDDEN, "该Worker没有产物提交权限");
                    }
                    var identity = context.get(com.agentsquad.squad.runtime.domain.WorkerExecutionIdentity.class);
                    if (identity == null || !bindingId.equals(identity.bindingId())) {
                        throw new RuntimeBusinessException(RuntimeExceptionCode.FORBIDDEN, "缺少本次Worker领取的固定执行身份");
                    }
                    guard = identity.guardToken();
                }
                return ToolResultBlock.text(RuntimePayloads.toJson(json, delivery.commit(context.getUserId(),
                        context.getSessionId(), guard, request.stageId(), request.expectedHash())));
            } catch (RuntimeBusinessException exception) {
                return ToolResultBlock.error(exception.getMessage());
            } catch (IllegalArgumentException exception) {
                return ToolResultBlock.error("产物提交结构无效");
            }
        }).subscribeOn(Schedulers.boundedElastic());
    }
}
