package com.agentsquad.squad.runtime.service;

import com.agentsquad.squad.common.exception.RuntimeBusinessException;
import com.agentsquad.squad.common.exception.RuntimeExceptionCode;
import com.agentsquad.squad.common.util.CanonicalJson;
import com.agentsquad.squad.common.util.RuntimePayloads;
import com.agentsquad.squad.runtime.domain.WorkerLaunchRow;
import com.agentsquad.squad.runtime.repository.WorkerDispatchMapper;
import com.agentsquad.squad.worker.WorkerDefinitionService;
import com.agentsquad.squad.worker.dto.WorkerDefinitionResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Iterator;
import java.util.Map;

/**
 * Worker 结构化结果候选与终态裁决。
 *
 * <p>候选只记录当前 guard/Turn，原生成功结束且计划未替代后才可成为最终结果。</p>
 */
@Service
public class WorkerResultService {

    @Resource
    private WorkerDispatchMapper dispatchMapper;

    @Resource
    private WorkerDefinitionService definitionService;

    @Resource
    private ObjectMapper objectMapper;

    /**
     * 校验当前业务 guard、计划版本和输出 Schema；同一候选重放返回原结果。
     */
    @Transactional
    public String propose(String userId, String bindingId, String guardToken,
                          String turnId, JsonNode result) {
        if (turnId == null || turnId.isBlank() || result == null) {
            throw new RuntimeBusinessException(RuntimeExceptionCode.VALIDATION, "Worker 结果参数无效");
        }
        WorkerLaunchRow job = dispatchMapper.findByBinding(userId, bindingId);
        if (job == null || !"RUNNING".equals(job.jobStatus())
                || !"ACTIVE".equals(job.planStatus()) || job.cancelRequestedAt() != null
                || !guardToken.equals(job.guardToken()) || job.guardLeaseUntil() == null
                || !job.guardLeaseUntil().isAfter(LocalDateTime.now(ZoneOffset.UTC))) {
            throw new RuntimeBusinessException(RuntimeExceptionCode.CONFLICT,
                    "Worker guard 或计划已失效");
        }
        WorkerDefinitionResponse definition = definitionService.requirePublished(
                job.workerKey(), job.workerVersion());
        validate(result, definition.outputSchema());
        String resultJson = RuntimePayloads.toJson(objectMapper, result);
        if (job.resultCandidateJson() != null) {
            if (guardToken.equals(job.candidateGuardToken()) && turnId.equals(job.candidateTurnId())
                    && CanonicalJson.sha256(objectMapper, resultJson)
                    .equals(CanonicalJson.sha256(objectMapper, job.resultCandidateJson()))) {
                return job.jobId();
            }
            throw new RuntimeBusinessException(RuntimeExceptionCode.CONFLICT,
                    "Worker 结果候选已由其他内容占用");
        }
        if (dispatchMapper.saveCandidate(userId, bindingId, guardToken, turnId,
                resultJson, LocalDateTime.now(ZoneOffset.UTC)) != 1) {
            throw new RuntimeBusinessException(RuntimeExceptionCode.CONFLICT,
                    "Worker 结果候选写入时 guard 已失效");
        }
        return job.jobId();
    }

    /**
     * 原生回合成功结束后才提交结果；候选缺失时保持 Job 可恢复状态供后续故障分支处理。
     */
    @Transactional
    public boolean finalizeSuccess(String userId, String bindingId, String turnId) {
        return dispatchMapper.finalizeSuccess(userId, bindingId, turnId,
                LocalDateTime.now(ZoneOffset.UTC)) == 1;
    }

    /**
     * 旧计划在途 Job 收到终结事件时保留证据，但不得写成功或推进新依赖。
     */
    @Transactional
    public boolean supersedeInFlight(String userId, String bindingId) {
        return dispatchMapper.supersedeInFlight(userId, bindingId) == 1;
    }

    /**
     * 原生失败时不将候选当作成功，保留候选和错误证据供恢复审计。
     */
    @Transactional
    public void markFailed(String userId, String bindingId) {
        dispatchMapper.markFailed(userId, bindingId);
    }

    /**
     * 原生回合未成功或缺失候选时进入恢复阻塞，保留候选供下一次恢复判定。
     */
    @Transactional
    public void markRecoveryBlocked(String userId, String bindingId) {
        dispatchMapper.markRecoveryBlocked(userId, bindingId);
    }

    private void validate(JsonNode value, JsonNode schema) {
        if (schema == null || schema.isEmpty()) {
            return;
        }
        if (!schema.isObject()) {
            throw new RuntimeBusinessException(RuntimeExceptionCode.VALIDATION, "Worker 输出 Schema 无效");
        }
        Iterator<Map.Entry<String, JsonNode>> fields = schema.fields();
        while (fields.hasNext()) {
            String key = fields.next().getKey();
            if (!java.util.Set.of("type", "properties", "required", "items", "enum",
                    "additionalProperties", "description").contains(key)) {
                throw new RuntimeBusinessException(RuntimeExceptionCode.VALIDATION,
                        "Worker 输出 Schema 含不支持的约束: " + key);
            }
        }
        String type = schema.path("type").asText("");
        boolean matches = switch (type) {
            case "" -> true;
            case "object" -> value.isObject();
            case "array" -> value.isArray();
            case "string" -> value.isTextual();
            case "integer" -> value.isIntegralNumber();
            case "number" -> value.isNumber();
            case "boolean" -> value.isBoolean();
            case "null" -> value.isNull();
            default -> false;
        };
        if (!matches || (schema.has("enum") && !contains(schema.path("enum"), value))) {
            throw new RuntimeBusinessException(RuntimeExceptionCode.VALIDATION,
                    "Worker 输出与 Schema 不匹配");
        }
        if (value.isObject()) {
            JsonNode required = schema.path("required");
            if (required.isArray()) {
                for (JsonNode name : required) {
                    if (!name.isTextual() || !value.has(name.asText())) {
                        throw new RuntimeBusinessException(RuntimeExceptionCode.VALIDATION,
                                "Worker 输出缺少必填字段");
                    }
                }
            }
            JsonNode properties = schema.path("properties");
            Iterator<Map.Entry<String, JsonNode>> members = value.fields();
            while (members.hasNext()) {
                Map.Entry<String, JsonNode> member = members.next();
                if (properties.has(member.getKey())) {
                    validate(member.getValue(), properties.get(member.getKey()));
                } else if (schema.path("additionalProperties").isBoolean()
                        && !schema.path("additionalProperties").booleanValue()) {
                    throw new RuntimeBusinessException(RuntimeExceptionCode.VALIDATION,
                            "Worker 输出包含未定义字段");
                }
            }
        }
        if (value.isArray() && schema.has("items")) {
            for (JsonNode item : value) {
                validate(item, schema.get("items"));
            }
        }
    }

    private boolean contains(JsonNode allowed, JsonNode value) {
        if (!allowed.isArray()) {
            return false;
        }
        for (JsonNode candidate : allowed) {
            if (candidate.equals(value)) {
                return true;
            }
        }
        return false;
    }
}
