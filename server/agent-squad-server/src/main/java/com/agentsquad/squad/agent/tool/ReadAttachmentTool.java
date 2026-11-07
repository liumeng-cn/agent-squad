package com.agentsquad.squad.agent.tool;

import com.agentsquad.squad.domain.entity.ArtifactDO;
import com.agentsquad.squad.service.ArtifactService;
import com.agentsquad.squad.common.exception.RuntimeBusinessException;
import com.agentsquad.squad.common.util.MaterialFormatter;
import com.agentsquad.squad.common.util.RuntimePayloads;
import com.agentsquad.squad.service.ExecutionScopeService;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.message.ToolResultBlock;
import io.agentscope.core.tool.AgentTool;
import io.agentscope.core.tool.ToolCallParam;
import jakarta.annotation.Resource;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/**
 * Master 按需读取附件工具。
 * <p>
 * 读取当前执行已关联的用户附件完整正文；归属与摘要由服务端校验，模型不能替换身份。
 * 用于上下文冻结时被截断的大文件按需读取。
 */
@Component
@ConditionalOnProperty(prefix = "agentsquad.delivery", name = "enabled", havingValue = "true")
public class ReadAttachmentTool implements AgentTool {

    private static final int MAX_READ_CHARACTERS = 100000;

    @Resource
    private ArtifactService artifacts;

    @Resource
    private ExecutionScopeService scopes;

    @Resource
    private ObjectMapper json;

    @Override
    public String getName() {
        return "read_attachment";
    }

    @Override
    public String getDescription() {
        return "读取当前执行已关联的用户附件完整正文。附件被上下文预算截断时，用此工具获取完整内容。"
                + "参数 artifactId 必须是当前执行已关联的附件 ID。";
    }

    @Override
    public Map<String, Object> getParameters() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "artifactId", Map.of("type", "string", "format", "uuid")),
                "required", List.of("artifactId"),
                "additionalProperties", false);
    }

    @Override
    public Mono<ToolResultBlock> callAsync(ToolCallParam param) {
        return Mono.fromCallable(() -> read(param))
                .subscribeOn(Schedulers.boundedElastic());
    }

    private ToolResultBlock read(ToolCallParam param) {
        RuntimeContext context = param.getRuntimeContext();
        if (context == null) {
            return ToolResultBlock.error("缺少原生运行上下文");
        }
        Object idValue = param.getInput().get("artifactId");
        if (!(idValue instanceof String artifactId) || artifactId.isBlank()) {
            return ToolResultBlock.error("附件 ID 无效");
        }
        try {
            String scopeId = scopes.scopeForSession(context.getUserId(), context.getSessionId());
            if (!artifacts.isLinkedToScope(context.getUserId(), scopeId, artifactId)) {
                return ToolResultBlock.error("附件不属于当前执行");
            }
            ArtifactDO artifact = artifacts.content(context.getUserId(), artifactId);
            String raw = new String(artifact.content(), StandardCharsets.UTF_8);
            String formatted = MaterialFormatter.format(raw, artifact.mediaType(), MAX_READ_CHARACTERS);
            boolean truncated = formatted.length() < raw.length();
            return ToolResultBlock.text(RuntimePayloads.toJson(json, Map.of(
                    "artifactId", artifact.artifactId(),
                    "fileName", artifact.fileName(),
                    "mediaType", artifact.mediaType(),
                    "sizeBytes", artifact.sizeBytes(),
                    "content", formatted,
                    "truncated", truncated)));
        } catch (RuntimeBusinessException exception) {
            return ToolResultBlock.error(exception.getMessage());
        } catch (IllegalArgumentException exception) {
            return ToolResultBlock.error("附件读取请求结构无效");
        }
    }
}
