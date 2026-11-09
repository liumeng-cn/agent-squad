package com.agentsquad.squad.agent.tool;

import com.agentsquad.squad.exceptions.RuntimeBusinessException;
import com.agentsquad.squad.service.WorkerResultService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.message.ToolResultBlock;
import io.agentscope.core.session.SessionRecorder;
import io.agentscope.core.tool.AgentTool;
import io.agentscope.core.tool.ToolCallParam;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.List;
import java.util.Map;

/**
 * 单个 Worker Binding 的结构化结果提交工具。
 *
 * <p>工具实例固定业务 guard，模型不能提供或替换令牌。</p>
 */
public class ReportJobResultTool implements AgentTool {

    private final WorkerResultService resultService;

    private final ObjectMapper objectMapper;

    private final String userId;

    private final String sessionId;

    private final String bindingId;

    private final String guardToken;

    private Map<String, Object> outputSchema = Map.of();

    public ReportJobResultTool(WorkerResultService resultService, ObjectMapper objectMapper,
                               String userId, String sessionId, String bindingId, String guardToken) {
        this.resultService = resultService;
        this.objectMapper = objectMapper;
        this.userId = userId;
        this.sessionId = sessionId;
        this.bindingId = bindingId;
        this.guardToken = guardToken;
    }

    @Override
    public String getName() {
        return "report_job_result";
    }

    /** 候选保存成功即结束当前回合，避免模型继续修改并重复提交已经固定的结果。 */
    @Override
    public boolean isReturnDirect() {
        return true;
    }

    @Override
    public String getDescription() {
        return "完成当前 Worker 节点前必须调用。提交符合输出 Schema 的结构化 result；"
                + "平台只在原生回合成功结束后确认节点结果。";
    }

    @Override
    public Map<String, Object> getParameters() {
        return Map.of("type", "object", "properties", Map.of(
                        "result", outputSchema),
                "required", List.of("result"), "additionalProperties", false);
    }

    /** 注册前绑定已发布版本的输出契约，让模型直接提交对应 JSON 类型，仍由服务端再次校验。 */
    public ReportJobResultTool outputSchema(JsonNode schema) {
        outputSchema = objectMapper.convertValue(schema,
                objectMapper.getTypeFactory().constructMapType(Map.class, String.class, Object.class));
        return this;
    }

    /**
     * DB 校验与写入在 boundedElastic 执行，工具回执不等于 Job 最终成功。
     */
    @Override
    public Mono<ToolResultBlock> callAsync(ToolCallParam param) {
        return Mono.fromCallable(() -> submit(param))
                .subscribeOn(Schedulers.boundedElastic());
    }

    private ToolResultBlock submit(ToolCallParam param) {
        RuntimeContext context = param.getRuntimeContext();
        if (context == null || !userId.equals(context.getUserId())
                || !sessionId.equals(context.getSessionId())) {
            return ToolResultBlock.error("Worker 结果会话不匹配");
        }
        String turnId = context.get(SessionRecorder.TURN_ID_KEY, String.class);
        JsonNode result = objectMapper.valueToTree(param.getInput().get("result"));
        try {
            String jobId = resultService.propose(userId, bindingId, guardToken, turnId, result);
            return ToolResultBlock.text("Worker 结果候选已保存，等待原生回合结束。jobId=" + jobId);
        } catch (RuntimeBusinessException exception) {
            return ToolResultBlock.error(exception.getMessage());
        }
    }
}
