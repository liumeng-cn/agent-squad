package com.agentsquad.squad.worker.service;

import com.agentsquad.squad.common.util.JsonSchemaValidator;
import com.agentsquad.squad.identity.AuthView;
import com.agentsquad.squad.common.exception.RuntimeBusinessException;
import com.agentsquad.squad.common.exception.RuntimeExceptionCode;
import com.agentsquad.squad.common.util.ContentDigests;
import com.agentsquad.squad.common.util.RuntimePayloads;
import com.agentsquad.squad.worker.domain.WorkerDefinitionRow;
import com.agentsquad.squad.worker.domain.WorkerDefinitionStatus;
import com.agentsquad.squad.worker.dto.WorkerDefinitionCreateRequest;
import com.agentsquad.squad.worker.dto.WorkerDefinitionPage;
import com.agentsquad.squad.worker.dto.WorkerDefinitionResponse;
import com.agentsquad.squad.worker.dto.WorkerStatusUpdateRequest;
import com.agentsquad.squad.worker.repository.WorkerDefinitionMapper;
import com.agentsquad.squad.worker.profile.WorkerProfileRegistry;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Worker 能力版本管理。
 * <p>
 * 发布后固定定义正文，只允许以乐观版本修改可调度状态；不创建 Worker 实例或执行模型。
 */
@Service
@Slf4j
public class WorkerDefinitionService {

    private static final Pattern WORKER_KEY = Pattern.compile("[a-z][a-z0-9_-]*");
    private static final Pattern VERSION = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._-]*");
    private static final Pattern REFERENCE_KEY = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._-]*");
    private static final Pattern TOOL_NAME = Pattern.compile("[a-z][a-z0-9_]*");
    private static final int MAX_DEFINITION_BYTES = 65536;
    private static final int MAX_SCHEMA_DEPTH = 8;

    @Resource
    private WorkerDefinitionMapper definitionMapper;

    @Resource
    private ObjectMapper objectMapper;

    @Resource
    private WorkerProfileRegistry profileRegistry;

    @Resource
    private JsonSchemaValidator schemaValidator;

    /**
     * 管理员按幂等键发布不可变版本；相同键换内容和同版本重复发布均拒绝。
     */
    public WorkerDefinitionResponse publish(AuthView actor, String idempotencyKey,
                                            WorkerDefinitionCreateRequest request) {
        requireAdmin(actor);
        requireIdempotencyKey(idempotencyKey);
        validateDefinition(request);
        String json = writeJson(request);
        if (json.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > MAX_DEFINITION_BYTES) {
            throw invalid("Worker 定义超过 64 KiB");
        }
        String hash = ContentDigests.sha256(json);

        WorkerDefinitionRow prior = definitionMapper.findByPublishKey(actor.userId(), idempotencyKey);
        if (prior != null) {
            return requireSamePayload(prior, hash);
        }

        String definitionId = UUID.randomUUID().toString();
        try {
            definitionMapper.insert(definitionId, request.workerKey(), request.workerVersion(),
                    json, idempotencyKey, hash, actor.userId(), actor.username());
        } catch (DuplicateKeyException exception) {
            WorkerDefinitionRow concurrent = definitionMapper.findByPublishKey(actor.userId(), idempotencyKey);
            if (concurrent != null) {
                return requireSamePayload(concurrent, hash);
            }
            throw new RuntimeBusinessException(RuntimeExceptionCode.CONFLICT, "Worker 能力版本已存在");
        }
        log.info("Worker 能力版本已发布 definitionId={} workerKey={} workerVersion={}",
                definitionId, request.workerKey(), request.workerVersion());
        return toResponse(definitionMapper.findByDefinitionId(definitionId));
    }

    /**
     * 按持久主键分页读取已发布定义，不启动或恢复任何 Worker。
     */
    public WorkerDefinitionPage list(String cursor, int limit) {
        long afterId = parseCursor(cursor);
        int pageSize = Math.min(Math.max(limit, 1), 100);
        List<WorkerDefinitionRow> rows = definitionMapper.listAfter(afterId, pageSize);
        String nextCursor = rows.size() == pageSize ? String.valueOf(rows.get(rows.size() - 1).id()) : null;
        return new WorkerDefinitionPage(rows.stream().map(this::toResponse).toList(), nextCursor);
    }

    /**
     * Master 按持久版本发现当前可调度能力；页游标只用于遍历，不成为执行授权。
     */
    public WorkerDefinitionPage listEnabled(String cursor, int limit) {
        long afterId = parseCursor(cursor);
        int pageSize = Math.min(Math.max(limit, 1), 20);
        List<WorkerDefinitionRow> rows = definitionMapper.listEnabledAfter(afterId, pageSize);
        String nextCursor = rows.size() == pageSize ? String.valueOf(rows.get(rows.size() - 1).id()) : null;
        return new WorkerDefinitionPage(rows.stream().map(this::toResponse).toList(), nextCursor);
    }

    /**
     * 派发前重新解析已启用的不可变能力版本；停用与撤销立即阻止新启动。
     */
    public WorkerDefinitionResponse requireEnabled(String workerKey, String workerVersion) {
        WorkerDefinitionRow row = definitionMapper.findEnabled(workerKey, workerVersion);
        if (row == null) {
            throw new RuntimeBusinessException(RuntimeExceptionCode.NOT_FOUND, "Worker 能力版本不可用");
        }
        return toResponse(row);
    }

    /**
     * 已启动 Job 恢复时读取不可变版本正文；停用只阻止新启动，不改变在途节点契约。
     */
    public WorkerDefinitionResponse requirePublished(String workerKey, String workerVersion) {
        WorkerDefinitionRow row = definitionMapper.findPublished(workerKey, workerVersion);
        if (row == null) {
            throw new RuntimeBusinessException(RuntimeExceptionCode.NOT_FOUND,
                    "Worker 能力版本不存在");
        }
        return toResponse(row);
    }

    /**
     * 状态变更只修改状态和版本。撤销后不可重新启用，旧版本正文始终可读。
     */
    @Transactional
    public WorkerDefinitionResponse changeStatus(AuthView actor, String definitionId,
                                                 String idempotencyKey, WorkerStatusUpdateRequest request) {
        requireAdmin(actor);
        requireIdempotencyKey(idempotencyKey);
        WorkerDefinitionRow current = requireDefinition(definitionId);
        String hash = ContentDigests.sha256(request.expectedVersion() + ":" + request.status());
        if (idempotencyKey.equals(current.lastStatusKey())
                && actor.userId().equals(current.lastStatusActor())) {
            if (!hash.equals(current.lastStatusHash())) {
                throw new RuntimeBusinessException(RuntimeExceptionCode.CONFLICT, "幂等键对应的状态请求不同");
            }
            return toResponse(current);
        }
        WorkerDefinitionStatus status = WorkerDefinitionStatus.valueOf(current.status());
        if (!status.canChangeTo(request.status())) {
            throw new RuntimeBusinessException(RuntimeExceptionCode.CONFLICT, "已撤销的 Worker 版本不能重新启用");
        }
        if (current.version() != request.expectedVersion()) {
            throw new RuntimeBusinessException(RuntimeExceptionCode.CONFLICT, "Worker 状态版本已变化");
        }
        int updated = definitionMapper.updateStatus(definitionId, request.expectedVersion(),
                request.status().name(), idempotencyKey, hash, actor.userId(), actor.username());
        if (updated != 1) {
            throw new RuntimeBusinessException(RuntimeExceptionCode.CONFLICT, "Worker 状态版本已变化");
        }
        log.info("Worker 能力状态已更新 definitionId={} status={} version={}",
                definitionId, request.status(), current.version() + 1);
        return toResponse(requireDefinition(definitionId));
    }

    private WorkerDefinitionRow requireDefinition(String definitionId) {
        WorkerDefinitionRow row = definitionMapper.findByDefinitionId(definitionId);
        if (row == null) {
            throw new RuntimeBusinessException(RuntimeExceptionCode.NOT_FOUND, "Worker 能力定义不存在");
        }
        return row;
    }

    private WorkerDefinitionResponse requireSamePayload(WorkerDefinitionRow row, String hash) {
        if (!hash.equals(row.payloadHash())) {
            throw new RuntimeBusinessException(RuntimeExceptionCode.CONFLICT, "幂等键对应的 Worker 定义不同");
        }
        return toResponse(row);
    }

    private WorkerDefinitionResponse toResponse(WorkerDefinitionRow row) {
        try {
            WorkerDefinitionCreateRequest definition = objectMapper.treeToValue(
                    RuntimePayloads.readJson(objectMapper, row.definitionJson()),
                    WorkerDefinitionCreateRequest.class);
            return new WorkerDefinitionResponse(row.definitionId(), row.workerKey(), row.workerVersion(),
                    definition.description(), definition.inputSchema(), definition.outputSchema(),
                    definition.toolAllowlist(), definition.modelProfileKey(), definition.runtimeProfileKey(),
                    row.status(), row.version());
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Worker 定义内容无法读取: " + row.definitionId(), exception);
        }
    }

    private void validateDefinition(WorkerDefinitionCreateRequest request) {
        validateCode(request.workerKey(), WORKER_KEY, 80, "workerKey");
        validateCode(request.workerVersion(), VERSION, 40, "workerVersion");
        validateCode(request.modelProfileKey(), REFERENCE_KEY, 80, "modelProfileKey");
        validateCode(request.runtimeProfileKey(), REFERENCE_KEY, 80, "runtimeProfileKey");
        profileRegistry.requireRegistered(request.modelProfileKey(), request.runtimeProfileKey());
        if (request.description() == null || request.description().isBlank()
                || request.description().length() > 4000) {
            throw invalid("description 长度无效");
        }
        validateSchema(request.inputSchema(), "inputSchema");
        validateSchema(request.outputSchema(), "outputSchema");
        if (request.toolAllowlist() == null || request.toolAllowlist().size() > 64) {
            throw invalid("toolAllowlist 数量无效");
        }
        HashSet<String> names = new HashSet<>();
        for (String tool : request.toolAllowlist()) {
            validateCode(tool, TOOL_NAME, 100, "toolAllowlist");
            if (!names.add(tool) || "propose_completion".equals(tool)) {
                throw invalid("toolAllowlist 含重复或 Master 专用工具");
            }
        }
    }

    private void validateSchema(JsonNode schema, String field) {
        if (schema == null || !schema.isObject() || depth(schema, 1) > MAX_SCHEMA_DEPTH) {
            throw invalid(field + " 必须是深度不超过 8 的 JSON 对象");
        }
        schemaValidator.requireValidSchema(schema, field);
    }

    private int depth(JsonNode node, int currentDepth) {
        if (currentDepth > MAX_SCHEMA_DEPTH || !node.isContainerNode()) {
            return currentDepth;
        }
        int deepest = currentDepth;
        for (JsonNode child : node) {
            deepest = Math.max(deepest, depth(child, currentDepth + 1));
        }
        return deepest;
    }

    private void validateCode(String value, Pattern pattern, int maxLength, String field) {
        if (value == null || value.length() > maxLength || !pattern.matcher(value).matches()) {
            throw invalid(field + " 格式无效");
        }
    }

    private String writeJson(WorkerDefinitionCreateRequest request) {
        try {
            return objectMapper.writeValueAsString(request);
        } catch (JsonProcessingException exception) {
            throw invalid("Worker 定义无法序列化");
        }
    }

    private long parseCursor(String cursor) {
        if (cursor == null || cursor.isBlank()) {
            return 0;
        }
        try {
            long value = Long.parseLong(cursor);
            if (value > 0) {
                return value;
            }
        } catch (NumberFormatException ignored) {
            // 非数字游标统一转换为业务参数错误。
        }
        throw invalid("cursor 格式无效");
    }

    private void requireIdempotencyKey(String key) {
        if (key == null || key.isBlank() || key.length() > 160) {
            throw invalid("Idempotency-Key 格式无效");
        }
    }

    private void requireAdmin(AuthView actor) {
        if (actor == null || !actor.roles().contains("ADMIN")) {
            throw new RuntimeBusinessException(RuntimeExceptionCode.FORBIDDEN, "需要管理员权限");
        }
    }

    private RuntimeBusinessException invalid(String message) {
        return new RuntimeBusinessException(RuntimeExceptionCode.VALIDATION, message);
    }
}
