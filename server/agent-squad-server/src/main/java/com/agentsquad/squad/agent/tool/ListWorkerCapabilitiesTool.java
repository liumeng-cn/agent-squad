package com.agentsquad.squad.agent.tool;

import com.agentsquad.squad.common.exception.RuntimeBusinessException;
import com.agentsquad.squad.common.util.RuntimePayloads;
import com.agentsquad.squad.worker.service.WorkerDefinitionService;
import com.agentsquad.squad.worker.dto.WorkerDefinitionPage;
import com.agentsquad.squad.worker.dto.WorkerDefinitionResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.agentscope.core.message.ToolResultBlock;
import io.agentscope.core.tool.AgentTool;
import io.agentscope.core.tool.ToolCallParam;
import jakarta.annotation.Resource;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Master 的 Worker 能力目录工具。
 *
 * 从持久 Definition 动态发现已启用版本；列表只给规划所需摘要，精确查询才返回完整契约。
 */
@Component
@ConditionalOnProperty(prefix = "agentsquad.delivery", name = "enabled", havingValue = "true")
public class ListWorkerCapabilitiesTool implements AgentTool {

    @Resource
    private WorkerDefinitionService definitionService;

    @Resource
    private ObjectMapper objectMapper;

    @Override
    public String getName() {
        return "list_worker_capabilities";
    }

    @Override
    public String getDescription() {
        return "列出当前可用的 Worker 能力版本。需要输入、输出 Schema 和工具权限时传 workerKey 与 workerVersion 查询详情。";
    }

    @Override
    public Map<String, Object> getParameters() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "cursor", Map.of("type", "string"),
                        "limit", Map.of("type", "integer", "minimum", 1, "maximum", 20),
                        "workerKey", Map.of("type", "string"),
                        "workerVersion", Map.of("type", "string")),
                "additionalProperties", false);
    }

    /**
     * 目录查询在线程池读取数据库；每次都读取当前状态，不缓存可调度资格。
     */
    @Override
    public Mono<ToolResultBlock> callAsync(ToolCallParam param) {
        return Mono.fromCallable(() -> discover(param))
                .subscribeOn(Schedulers.boundedElastic());
    }

    private ToolResultBlock discover(ToolCallParam param) {
        if (param.getRuntimeContext() == null) {
            return ToolResultBlock.error("缺少原生运行上下文");
        }
        Map<String, Object> input = param.getInput();
        if (input == null) {
            return ToolResultBlock.error("能力目录参数无效");
        }
        Object keyValue = input.get("workerKey");
        Object versionValue = input.get("workerVersion");
        if (keyValue != null || versionValue != null) {
            if (!(keyValue instanceof String key) || !(versionValue instanceof String version)) {
                return ToolResultBlock.error("须同时指定 workerKey 和 workerVersion");
            }
            try {
                WorkerDefinitionResponse definition = definitionService.requireEnabled(key, version);
                return ToolResultBlock.text(RuntimePayloads.toJson(objectMapper, definition));
            } catch (RuntimeBusinessException exception) {
                return ToolResultBlock.error(exception.getMessage());
            }
        }

        Object cursorValue = input.get("cursor");
        Object limitValue = input.get("limit");
        if (cursorValue != null && !(cursorValue instanceof String)
                || limitValue != null && !(limitValue instanceof Integer)) {
            return ToolResultBlock.error("能力目录分页参数无效");
        }
        String cursor = (String) cursorValue;
        int limit = limitValue == null ? 10 : (Integer) limitValue;
        if (limit < 1 || limit > 20) {
            return ToolResultBlock.error("能力目录每页最多 20 条");
        }
        try {
            WorkerDefinitionPage page = definitionService.listEnabled(cursor, limit);
            List<Map<String, String>> summaries = page.items().stream()
                    .map(definition -> Map.of(
                            "workerKey", definition.workerKey(),
                            "workerVersion", definition.workerVersion(),
                            "description", definition.description()))
                    .toList();
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("items", summaries);
            result.put("nextCursor", page.nextCursor());
            return ToolResultBlock.text(RuntimePayloads.toJson(objectMapper, result));
        } catch (RuntimeBusinessException exception) {
            return ToolResultBlock.error(exception.getMessage());
        }
    }
}
