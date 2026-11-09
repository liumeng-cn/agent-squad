package com.agentsquad.squad.agent.tool;

import com.agentsquad.squad.agent.RuntimeContextIds;

import com.agentsquad.squad.model.po.ArtifactPO;
import com.agentsquad.squad.service.ArtifactService;
import com.agentsquad.squad.exceptions.RuntimeBusinessException;
import com.agentsquad.squad.utils.MaterialFormatter;
import com.agentsquad.squad.utils.RuntimePayloads;
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
                        "artifactId", Map.of("type", "string")),
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
        Long artifactId = parseId(idValue);
        if (artifactId == null) {
            return ToolResultBlock.error("附件 ID 无效");
        }
        try {
            Long scopeId = scopes.scopeForSession(RuntimeContextIds.userId(context), context.getSessionId());
            if (!artifacts.isLinkedToScope(RuntimeContextIds.userId(context), scopeId, artifactId)) {
                return ToolResultBlock.error("附件不属于当前执行");
            }
            ArtifactPO artifact = artifacts.content(RuntimeContextIds.userId(context), artifactId);
            String raw = new String(artifact.getContent(), StandardCharsets.UTF_8);
            String formatted = MaterialFormatter.format(raw, artifact.getMediaType(), MAX_READ_CHARACTERS);
            boolean truncated = formatted.length() < raw.length();
            return ToolResultBlock.text(RuntimePayloads.toJson(json, Map.of(
                    "artifactId", artifact.getArtifactId(),
                    "fileName", artifact.getFileName(),
                    "mediaType", artifact.getMediaType(),
                    "sizeBytes", artifact.getSizeBytes(),
                    "content", formatted,
                    "truncated", truncated)));
        } catch (RuntimeBusinessException exception) {
            return ToolResultBlock.error(exception.getMessage());
        } catch (IllegalArgumentException exception) {
            return ToolResultBlock.error("附件读取请求结构无效");
        }
    }

    /**
     * 模型可能以数字或字符串传入大整数 ID，统一解析为正数 Long；无法解析按无效参数处理。
     */
    private Long parseId(Object value) {
        try {
            long parsed;
            if (value instanceof Number number) {
                parsed = number.longValue();
            } else if (value instanceof String text && !text.isBlank()) {
                parsed = Long.parseLong(text.trim());
            } else {
                return null;
            }
            return parsed > 0 ? parsed : null;
        } catch (NumberFormatException exception) {
            return null;
        }
    }
}
