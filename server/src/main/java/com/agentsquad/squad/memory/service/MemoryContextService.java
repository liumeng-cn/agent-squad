package com.agentsquad.squad.memory.service;

import com.agentsquad.squad.common.exception.RuntimeBusinessException;
import com.agentsquad.squad.common.exception.RuntimeExceptionCode;
import com.agentsquad.squad.conversation.service.ExecutionScopeService;
import com.agentsquad.squad.memory.domain.MemoryContextInvalidatedException;
import com.agentsquad.squad.memory.domain.MemoryRow;
import com.agentsquad.squad.memory.domain.MemoryRules;
import com.agentsquad.squad.memory.domain.MemoryScopePin;
import com.agentsquad.squad.memory.dto.MemoryContextSnapshot;
import com.agentsquad.squad.memory.dto.MemoryReference;
import com.agentsquad.squad.memory.repository.MemoryMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import java.util.HashSet;

/**
 * 运行上下文的记忆冻结与失效屏障。
 * <p>
 * Scope第一次装配固定最多20条有效记忆；后续恢复只读固定引用，纠正、删除及过期均拒绝旧会话继续使用。
 */
@Service
public class MemoryContextService {
    private static final int MAX_REFERENCES = 20;

    @Resource
    private MemoryMapper mapper;

    @Resource
    private ExecutionScopeService scopes;

    /**
     * 在会话/所有者之后锁用户代际，原子冻结引用；不调用模型，不把正文复制到另一个可复活的存储。
     */
    @Transactional
    public MemoryContextSnapshot freeze(String userId, String scopeId) {
        scopes.lockOwned(userId, scopeId, true);
        long revision = lockRevision(userId);
        MemoryScopePin prior = mapper.pin(userId, scopeId);
        if (prior != null) {
            return currentSnapshot(userId, scopeId, prior, revision);
        }
        List<MemoryRow> memories = mapper.active(userId, now(), MAX_REFERENCES);
        mapper.pinScope(userId, scopeId, revision);
        for (MemoryRow memory : memories) {
            mapper.pinItem(userId, scopeId, memory.memoryId(), memory.version());
        }
        return new MemoryContextSnapshot(revision, memories.stream().map(this::reference).toList());
    }

    /**
     * 校验与调用方事务同生共死；用户代际锁保留到提交，序列化删除与结果发布。
     * 调用方须先取得会话/实际所有者锁；此处不反向取得所有者锁。
     */
    @Transactional
    public void requireCurrent(String userId, String scopeId) {
        scopes.requireReadable(userId, scopeId);
        long revision = lockRevision(userId);
        MemoryScopePin pin = mapper.pin(userId, scopeId);
        if (pin != null) {
            currentSnapshot(userId, scopeId, pin, revision);
        }
    }

    /**
     * 返回已经冻结并复验的资料；没有冻结点时拒绝读取，不临时获取新记忆污染旧上下文。
     */
    @Transactional
    public MemoryContextSnapshot read(String userId, String scopeId) {
        scopes.requireReadable(userId, scopeId);
        long revision = lockRevision(userId);
        MemoryScopePin pin = mapper.pin(userId, scopeId);
        if (pin == null) {
            throw new RuntimeBusinessException(RuntimeExceptionCode.CONFLICT, "执行尚未固定记忆上下文");
        }
        return currentSnapshot(userId, scopeId, pin, revision);
    }

    /**
     * Worker只取得调用方明确选择的固定引用，不将Master的整个记忆集合默认下发。
     */
    @Transactional
    public MemoryContextSnapshot select(String userId, String scopeId, List<String> memoryIds) {
        if (memoryIds == null || memoryIds.size() > MAX_REFERENCES
                || memoryIds.stream().anyMatch(id -> id == null || id.isBlank())
                || memoryIds.stream().distinct().count() != memoryIds.size()) {
            throw new RuntimeBusinessException(RuntimeExceptionCode.VALIDATION, "局部记忆引用必须唯一且不超过20条");
        }
        MemoryContextSnapshot pinned = read(userId, scopeId);
        List<MemoryReference> selected = pinned.memories().stream()
                .filter(memory -> memoryIds.contains(memory.memoryId())).toList();
        if (selected.size() != memoryIds.size()) {
            throw new RuntimeBusinessException(RuntimeExceptionCode.FORBIDDEN, "局部记忆不属于该执行的固定授权范围");
        }
        return new MemoryContextSnapshot(pinned.memoryRevision(), selected);
    }

    /**
     * 已撤销显式授权的原话来源；会话装配据此排除旧来源与其派生助手回复，不删除用户聊天账本。
     */
    @Transactional
    public List<String> invalidatedSourceMessageIds(String userId) {
        lockRevision(userId);
        return mapper.invalidatedSources(userId, now());
    }

    /**
     * 排除失效记忆来源及其派生助手消息；原聊天账本保留，装配方以安全标记替换内容并保留序号。
     * 当前读先取得失效引用，再按ID关联历史，避免MySQL可重复读快照放回已撤销来源。
     */
    @Transactional
    public Set<String> excludedHistoryMessageIds(String userId) {
        long revision = lockRevision(userId);
        Set<String> excluded = new HashSet<>();
        for (String sourceId : mapper.invalidatedSources(userId, now())) {
            excluded.add(sourceId);
            excluded.addAll(mapper.sourceMessages(userId, sourceId));
        }
        Set<String> staleScopes = new HashSet<>(mapper.blockedScopeIds(userId, revision));
        staleScopes.addAll(mapper.staleScopeIds(userId, now()));
        for (String scopeId : staleScopes) {
            excluded.addAll(mapper.scopeMessages(userId, scopeId));
        }
        return Set.copyOf(excluded);
    }

    private MemoryContextSnapshot currentSnapshot(String userId, String scopeId, MemoryScopePin pin, long revision) {
        if (!"CURRENT".equals(pin.barrierStatus()) || pin.memoryRevision() != revision) {
            throw new MemoryContextInvalidatedException();
        }
        LocalDateTime timestamp = now();
        List<MemoryReference> memories = mapper.pinnedIds(userId, scopeId).stream().map(id -> {
            MemoryRow current = mapper.find(userId, id);
            Long expected = mapper.pinnedVersion(userId, scopeId, id);
            if (current == null || expected == null || current.version() != expected
                    || !MemoryRules.active(current, timestamp)) {
                throw new MemoryContextInvalidatedException();
            }
            return reference(current);
        }).toList();
        return new MemoryContextSnapshot(pin.memoryRevision(), memories);
    }

    private long lockRevision(String userId) {
        mapper.ensureRevision(userId);
        return mapper.lockRevision(userId);
    }

    private MemoryReference reference(MemoryRow row) {
        return new MemoryReference(row.memoryId(), row.key(), row.content(), row.version(), row.expiresAt());
    }

    private LocalDateTime now() {
        return LocalDateTime.now(ZoneOffset.UTC);
    }
}
