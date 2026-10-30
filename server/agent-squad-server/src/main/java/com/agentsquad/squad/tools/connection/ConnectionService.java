package com.agentsquad.squad.tools.connection;

import com.agentsquad.squad.identity.AuthView;
import com.agentsquad.squad.common.exception.RuntimeBusinessException;
import com.agentsquad.squad.common.exception.RuntimeExceptionCode;
import com.agentsquad.squad.common.util.RuntimePayloads;
import com.agentsquad.squad.tools.domain.ConnectionRow;
import com.agentsquad.squad.tools.dto.ConnectionCreateRequest;
import com.agentsquad.squad.tools.dto.ConnectionPage;
import com.agentsquad.squad.tools.dto.ConnectionResponse;
import com.agentsquad.squad.tools.dto.ConnectionUpdateRequest;
import com.agentsquad.squad.tools.repository.ConnectionMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * 用户外部连接管理（M01 归属模型 + M07 密钥引用）。
 *
 * <p>归属只取认证会话；密钥经本地保险库加密后落库，接口与视图不含明文。
 * PATCH 以 expectedVersion 对应的 revision 做乐观并发控制并递增 revision；
 * revision 是后续审批与授权绑定的版本依据，不能跳变。</p>
 */
@Service
@Slf4j
public class ConnectionService {

    private static final Pattern CODE_PATTERN = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._-]*");
    private static final int MAX_POLICY_JSON_BYTES = 8192;
    private static final int MAX_POLICY_DEPTH = 8;

    @Resource
    private ConnectionMapper connectionMapper;

    @Resource
    private LocalSecretVault secretVault;

    @Resource
    private ObjectMapper objectMapper;

    /**
     * 创建连接并加密密钥；同用户同 Idempotency-Key 复用原连接，不产生重复资源。
     */
    @Transactional
    public ConnectionResponse create(AuthView actor, String idempotencyKey,
                                     ConnectionCreateRequest request) {
        requireIdempotencyKey(idempotencyKey);
        validateName(request.name());
        validateCode("provider", request.provider());
        validateCode("environment", request.environment());
        String policyJson = validatePolicy(request.resourcePolicy());

        ConnectionRow prior = connectionMapper.findByIdempotencyKey(actor.userId(), idempotencyKey);
        if (prior != null) {
            log.info("连接创建幂等复用 userId={} connectionId={}", actor.userId(), prior.connectionId());
            return toView(prior);
        }

        String connectionId = UUID.randomUUID().toString();
        String secretRef = secretVault.store(request.secret());
        connectionMapper.insert(connectionId, actor.userId(), actor.username(), request.name(),
                request.provider(), request.environment(), policyJson, secretRef, idempotencyKey);
        log.info("连接创建成功 userId={} connectionId={} provider={} environment={}",
                actor.userId(), connectionId, request.provider(), request.environment());
        return toView(connectionMapper.findOwned(actor.userId(), connectionId));
    }

    /**
     * 分页列出当前用户连接；cursor 为存储主键，空表示首页。
     */
    public ConnectionPage list(String userId, String cursor, int limit) {
        Long cursorId = parseCursor(cursor);
        List<ConnectionRow> rows = connectionMapper.listByOwner(userId, cursorId,
                Math.min(Math.max(limit, 1), 100));
        String nextCursor = rows.size() == Math.min(Math.max(limit, 1), 100)
                ? String.valueOf(rows.get(rows.size() - 1).id())
                : null;
        return new ConnectionPage(rows.stream().map(this::toView).toList(), nextCursor);
    }

    /**
     * 读取当前用户单个连接视图。
     */
    public ConnectionResponse get(String userId, String connectionId) {
        return toView(requireOwned(userId, connectionId));
    }

    /**
     * 按 expectedVersion 更新配置或轮换密钥；每次成功更新 revision 加一。
     */
    @Transactional
    public ConnectionResponse update(AuthView actor, String connectionId, String idempotencyKey,
                                     ConnectionUpdateRequest request) {
        requireIdempotencyKey(idempotencyKey);
        ConnectionRow row = requireOwned(actor.userId(), connectionId);
        if (request.expectedVersion() == null || request.expectedVersion() != row.revision()) {
            throw new RuntimeBusinessException(RuntimeExceptionCode.CONFLICT, "连接版本已变化，请刷新后重试");
        }
        String policyJson = request.resourcePolicy() == null ? null : validatePolicy(request.resourcePolicy());
        if (request.secret() != null) {
            secretVault.rotate(row.secretRef(), request.secret());
        }
        int updated = connectionMapper.updateOwned(actor.userId(), connectionId, row.revision(),
                policyJson, request.enabled(), actor.username());
        if (updated != 1) {
            throw new RuntimeBusinessException(RuntimeExceptionCode.CONFLICT, "连接版本已变化，请刷新后重试");
        }
        log.info("连接更新成功 userId={} connectionId={} fromRevision={} secretRotated={}",
                actor.userId(), connectionId, row.revision(), request.secret() != null);
        return toView(connectionMapper.findOwned(actor.userId(), connectionId));
    }

    private ConnectionRow requireOwned(String userId, String connectionId) {
        ConnectionRow row = connectionMapper.findOwned(userId, connectionId);
        if (row == null) {
            throw new RuntimeBusinessException(RuntimeExceptionCode.NOT_FOUND, "连接不存在");
        }
        return row;
    }

    private void requireIdempotencyKey(String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank() || idempotencyKey.length() > 160) {
            throw new RuntimeBusinessException(RuntimeExceptionCode.VALIDATION,
                    "Idempotency-Key 必须是 1-160 个字符");
        }
    }

    private Long parseCursor(String cursor) {
        if (cursor == null || cursor.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(cursor);
        } catch (NumberFormatException exception) {
            throw new RuntimeBusinessException(RuntimeExceptionCode.VALIDATION, "cursor 游标无效");
        }
    }

    private void validateName(String name) {
        if (name == null || name.isBlank() || name.length() > 100) {
            throw new RuntimeBusinessException(RuntimeExceptionCode.VALIDATION, "名称必须是 1-100 个字符");
        }
    }

    private void validateCode(String field, String value) {
        if (value == null || value.isBlank() || value.length() > 80
                || !CODE_PATTERN.matcher(value).matches()) {
            throw new RuntimeBusinessException(RuntimeExceptionCode.VALIDATION,
                    field + " 只允许字母数字开头，含 . _ -，长度 1-80");
        }
    }

    /**
     * 资源策略必须是 JSON 对象，深度与字节受限；超限直接拒绝。
     */
    private String validatePolicy(Map<String, Object> resourcePolicy) {
        if (resourcePolicy == null || resourcePolicy.isEmpty()) {
            throw new RuntimeBusinessException(RuntimeExceptionCode.VALIDATION,
                    "resourcePolicy 必须是非空 JSON 对象");
        }
        if (resourcePolicy.keySet().stream().anyMatch(Objects::isNull)) {
            throw new RuntimeBusinessException(RuntimeExceptionCode.VALIDATION, "resourcePolicy 键不能为空");
        }
        String json = RuntimePayloads.toJson(objectMapper, resourcePolicy);
        if (json.getBytes(StandardCharsets.UTF_8).length > MAX_POLICY_JSON_BYTES) {
            throw new RuntimeBusinessException(RuntimeExceptionCode.VALIDATION, "resourcePolicy 序列化后超过 8KB");
        }
        try {
            JsonNode node = objectMapper.readTree(json);
            if (!node.isObject() || depth(node) > MAX_POLICY_DEPTH) {
                throw new RuntimeBusinessException(RuntimeExceptionCode.VALIDATION,
                        "resourcePolicy 必须是深度不超过 " + MAX_POLICY_DEPTH + " 的 JSON 对象");
            }
        } catch (RuntimeBusinessException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new RuntimeBusinessException(RuntimeExceptionCode.VALIDATION, "resourcePolicy 不是有效 JSON 对象");
        }
        return json;
    }

    private int depth(JsonNode node) {
        int max = 0;
        for (JsonNode child : node) {
            max = Math.max(max, child.isContainerNode() ? 1 + depth(child) : 1);
        }
        return max;
    }

    private ConnectionResponse toView(ConnectionRow row) {
        Map<String, Object> policy;
        try {
            JsonNode node = RuntimePayloads.readJson(objectMapper, row.resourcePolicyJson());
            policy = objectMapper.convertValue(node, objectMapper.getTypeFactory()
                    .constructMapType(Map.class, String.class, Object.class));
        } catch (Exception exception) {
            throw new IllegalStateException("连接资源策略无法解析", exception);
        }
        return new ConnectionResponse(row.connectionId(), row.name(), row.provider(),
                row.environment(), policy, true, row.revision(), row.enabled());
    }
}
