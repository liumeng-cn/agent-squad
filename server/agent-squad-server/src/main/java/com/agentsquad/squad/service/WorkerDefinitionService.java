package com.agentsquad.squad.service;

import com.agentsquad.squad.enums.AccountRole;
import com.agentsquad.squad.enums.WorkerDefinitionStatus;
import com.agentsquad.squad.exceptions.RuntimeBusinessException;
import com.agentsquad.squad.enums.RuntimeExceptionCode;
import com.agentsquad.squad.utils.ContentDigests;
import com.agentsquad.squad.utils.JsonSchemaValidator;
import com.agentsquad.squad.utils.RuntimePayloads;
import com.agentsquad.squad.identity.AuthView;
import com.agentsquad.squad.model.po.WorkerDefinitionPO;
import com.agentsquad.squad.model.dto.WorkerDefinitionCreateDTO;
import com.agentsquad.squad.model.vo.WorkerDefinitionPageVO;
import com.agentsquad.squad.model.vo.WorkerDefinitionVO;
import com.agentsquad.squad.model.dto.WorkerStatusUpdateDTO;
import com.agentsquad.squad.config.properties.WorkerProfileRegistry;
import com.agentsquad.squad.mapper.WorkerDefinitionMapper;
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
    public WorkerDefinitionVO publish(AuthView actor, String idempotencyKey,
                                      WorkerDefinitionCreateDTO request) {
        requireAdmin(actor);
        requireIdempotencyKey(idempotencyKey);
        validateDefinition(request);
        String json = writeJson(request);
        if (json.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > MAX_DEFINITION_BYTES) {
            throw invalid("Worker 定义超过 64 KiB");
        }
        String hash = ContentDigests.sha256(json);

        WorkerDefinitionPO prior = definitionMapper.findByPublishKey(actor.userId(), idempotencyKey);
        if (prior != null) {
            return requireSamePayload(prior, hash);
        }

        String definitionId = UUID.randomUUID().toString();
        try {
            definitionMapper.insertDefinition(definitionId, request.workerKey(), request.workerVersion(),
                    json, idempotencyKey, hash, actor.userId(), actor.username());
        } catch (DuplicateKeyException exception) {
            WorkerDefinitionPO concurrent = definitionMapper.findByPublishKey(actor.userId(), idempotencyKey);
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
    public WorkerDefinitionPageVO list(String cursor, int limit) {
        long afterId = parseCursor(cursor);
        int pageSize = Math.clamp(limit, 1, 100);
        List<WorkerDefinitionPO> rows = definitionMapper.listAfter(afterId, pageSize);
        String nextCursor = rows.size() == pageSize ? String.valueOf(rows.getLast().getId()) : null;
        return new WorkerDefinitionPageVO(rows.stream().map(this::toResponse).toList(), nextCursor);
    }

    /**
     * Master 按持久版本发现当前可调度能力；页游标只用于遍历，不成为执行授权。
     */
    public WorkerDefinitionPageVO listEnabled(String cursor, int limit) {
        long afterId = parseCursor(cursor);
        int pageSize = Math.clamp(limit, 1, 20);
        List<WorkerDefinitionPO> rows = definitionMapper.listEnabledAfter(afterId, pageSize);
        String nextCursor = rows.size() == pageSize ? String.valueOf(rows.get(rows.size() - 1).getId()) : null;
        return new WorkerDefinitionPageVO(rows.stream().map(this::toResponse).toList(), nextCursor);
    }

    /**
     * 派发前重新解析已启用的不可变能力版本；停用与撤销立即阻止新启动。
     */
    public WorkerDefinitionVO requireEnabled(String workerKey, String workerVersion) {
        WorkerDefinitionPO row = definitionMapper.findEnabled(workerKey, workerVersion);
        if (row == null) {
            throw new RuntimeBusinessException(RuntimeExceptionCode.NOT_FOUND, "Worker 能力版本不可用");
        }
        return toResponse(row);
    }

    /**
     * 已启动 Job 恢复时读取不可变版本正文；停用只阻止新启动，不改变在途节点契约。
     */
    public WorkerDefinitionVO requirePublished(String workerKey, String workerVersion) {
        WorkerDefinitionPO row = definitionMapper.findPublished(workerKey, workerVersion);
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
    public WorkerDefinitionVO changeStatus(AuthView actor, String definitionId,
                                           String idempotencyKey, WorkerStatusUpdateDTO request) {
        requireAdmin(actor);
        requireIdempotencyKey(idempotencyKey);
        WorkerDefinitionPO current = requireDefinition(definitionId);
        String hash = ContentDigests.sha256(request.expectedVersion() + ":" + request.status());
        if (idempotencyKey.equals(current.getLastStatusKey())
                && actor.userId().equals(current.getLastStatusActor())) {
            if (!hash.equals(current.getLastStatusHash())) {
                throw new RuntimeBusinessException(RuntimeExceptionCode.CONFLICT, "幂等键对应的状态请求不同");
            }
            return toResponse(current);
        }
        WorkerDefinitionStatus status = current.getStatus();
        if (!status.canChangeTo(request.status())) {
            throw new RuntimeBusinessException(RuntimeExceptionCode.CONFLICT, "已撤销的 Worker 版本不能重新启用");
        }
        if (current.getVersion() != request.expectedVersion()) {
            throw new RuntimeBusinessException(RuntimeExceptionCode.CONFLICT, "Worker 状态版本已变化");
        }
        int updated = definitionMapper.updateStatus(definitionId, request.expectedVersion(),
                request.status(), idempotencyKey, hash, actor.userId(), actor.username());
        if (updated != 1) {
            throw new RuntimeBusinessException(RuntimeExceptionCode.CONFLICT, "Worker 状态版本已变化");
        }
        log.info("Worker 能力状态已更新 definitionId={} status={} version={}",
                definitionId, request.status(), current.getVersion() + 1);
        return toResponse(requireDefinition(definitionId));
    }

    private WorkerDefinitionPO requireDefinition(String definitionId) {
        WorkerDefinitionPO row = definitionMapper.findByDefinitionId(definitionId);
        if (row == null) {
            throw new RuntimeBusinessException(RuntimeExceptionCode.NOT_FOUND, "Worker 能力定义不存在");
        }
        return row;
    }

    private WorkerDefinitionVO requireSamePayload(WorkerDefinitionPO row, String hash) {
        if (!hash.equals(row.getPayloadHash())) {
            throw new RuntimeBusinessException(RuntimeExceptionCode.CONFLICT, "幂等键对应的 Worker 定义不同");
        }
        return toResponse(row);
    }

    private WorkerDefinitionVO toResponse(WorkerDefinitionPO row) {
        try {
            WorkerDefinitionCreateDTO definition = objectMapper.treeToValue(
                    RuntimePayloads.readJson(objectMapper, row.getDefinitionJson()),
                    WorkerDefinitionCreateDTO.class);
            return new WorkerDefinitionVO(row.getDefinitionId(), row.getWorkerKey(), row.getWorkerVersion(),
                    definition.description(), definition.inputSchema(), definition.outputSchema(),
                    definition.toolAllowlist(), definition.modelProfileKey(), definition.runtimeProfileKey(),
                    row.getStatus(), row.getVersion());
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Worker 定义内容无法读取: " + row.getDefinitionId(), exception);
        }
    }

    private void validateDefinition(WorkerDefinitionCreateDTO request) {
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

    private String writeJson(WorkerDefinitionCreateDTO request) {
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
        if (actor == null || !actor.roles().contains(AccountRole.ADMIN)) {
            throw new RuntimeBusinessException(RuntimeExceptionCode.FORBIDDEN, "需要管理员权限");
        }
    }

    private RuntimeBusinessException invalid(String message) {
        return new RuntimeBusinessException(RuntimeExceptionCode.VALIDATION, message);
    }
}
