package com.agentsquad.squad.memory.service;

import com.agentsquad.squad.common.exception.RuntimeBusinessException;
import com.agentsquad.squad.common.exception.RuntimeExceptionCode;
import com.agentsquad.squad.memory.domain.MemoryRow;
import com.agentsquad.squad.memory.domain.MemoryRules;
import com.agentsquad.squad.memory.dto.MemoryCreate;
import com.agentsquad.squad.memory.dto.MemoryMutationResult;
import com.agentsquad.squad.memory.dto.MemoryPage;
import com.agentsquad.squad.memory.dto.MemoryUpdate;
import com.agentsquad.squad.memory.dto.MemoryView;
import com.agentsquad.squad.memory.repository.MemoryMapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

/**
 * 用户显式长期记忆的管理服务。
 * <p>
 * 所有变更在用户记忆代际锁下完成；纠正与删除原子推进失效屏障，不负责清理SDK会话。
 */
@Service
@Slf4j
public class MemoryService {
    @Resource
    private MemoryMapper mapper;

    public MemoryPage list(String userId, String cursor, int limit) {
        Long before = parseCursor(cursor);
        int size = Math.min(Math.max(limit, 1), 100);
        List<MemoryRow> rows = mapper.list(userId, before, size + 1);
        boolean more = rows.size() > size;
        List<MemoryRow> page = more ? rows.subList(0, size) : rows;
        String next = more ? Long.toString(page.getLast().storageId()) : null;
        return new MemoryPage(page.stream().map(this::view).toList(), next,
                mapper.revision(userId), mapper.blockedScopes(userId));
    }

    /**
     * 用户设置页明确创建；有效同键同内容返回原记录，异内容走带版本纠正；删除后的同键创建使用新身份，保留旧墓碑。
     */
    @Transactional
    public MemoryMutationResult create(String userId, MemoryCreate request) {
        return createFromSource(userId, request, null, null);
    }

    /**
     * 来源引用由受权工具入口提供，普通设置页不能伪造来源消息。
     */
    @Transactional
    public MemoryMutationResult createFromSource(String userId, MemoryCreate request,
                                                 String sourceMessageId, String sourceQuote) {
        MemoryRules.requireContent(request.key(), request.content(), utc(request.expiresAt()), now());
        lockRevision(userId);
        MemoryRow prior = mapper.findActiveKey(userId, request.key());
        if (prior != null) {
            if ("ACTIVE".equals(prior.status()) && prior.content().equals(request.content())
                    && java.util.Objects.equals(prior.expiresAt(), utc(request.expiresAt()))) {
                if (sourceMessageId != null) {
                    mapper.recordSource(userId, prior.memoryId(), prior.version(), sourceMessageId);
                }
                return result(userId, prior);
            }
            throw conflict("这个记忆键已有有效记录，请刷新后纠正现有内容");
        }
        String id = UUID.randomUUID().toString();
        mapper.insert(id, userId, request.key(), request.content(), utc(request.expiresAt()), sourceMessageId, sourceQuote);
        if (sourceMessageId != null) {
            mapper.recordSource(userId, id, 0, sourceMessageId);
        }
        log.info("显式记忆创建完成 userId={} memoryId={} sourceMessageId={}", userId, id, sourceMessageId);
        return result(userId, requireOwned(userId, id));
    }

    /**
     * 纠正正文与失效代际同事务提交；旧Scope引用只能阻塞，不能原位变成新版本。
     */
    @Transactional
    public MemoryMutationResult update(String userId, String memoryId, MemoryUpdate request) {
        return updateFromSource(userId, memoryId, request, null, null);
    }

    @Transactional
    public MemoryMutationResult updateFromSource(String userId, String memoryId, MemoryUpdate request,
                                                 String sourceMessageId, String sourceQuote) {
        lockRevision(userId);
        MemoryRow current = requireOwned(userId, memoryId);
        MemoryRules.requireContent(current.key(), request.content(), utc(request.expiresAt()), now());
        MemoryRules.requireVersion(current, request.expectedVersion());
        if (mapper.update(userId, memoryId, request.expectedVersion(), request.content(), utc(request.expiresAt()), sourceMessageId, sourceQuote) != 1) {
            throw conflict("记忆版本已变化，请刷新后纠正");
        }
        mapper.invalidateSources(userId, memoryId);
        if (sourceMessageId != null) {
            mapper.recordSource(userId, memoryId, request.expectedVersion() + 1, sourceMessageId);
        }
        invalidate(userId);
        log.info("显式记忆纠正完成 userId={} memoryId={} version={}", userId, memoryId, request.expectedVersion() + 1);
        return result(userId, requireOwned(userId, memoryId));
    }

    /**
     * 删除先写墓碑并清空正文，再原子阻塞所有旧代际上下文；不声称已撤回已发出的模型调用。
     */
    @Transactional
    public MemoryMutationResult delete(String userId, String memoryId, long expectedVersion) {
        lockRevision(userId);
        MemoryRow current = requireOwned(userId, memoryId);
        if ("DELETED".equals(current.status()) && current.version() == expectedVersion + 1) {
            return result(userId, current);
        }
        MemoryRules.requireVersion(current, expectedVersion);
        if (mapper.delete(userId, memoryId, expectedVersion) != 1) {
            throw conflict("记忆版本已变化，请刷新后删除");
        }
        mapper.invalidateSources(userId, memoryId);
        invalidate(userId);
        log.info("显式记忆删除屏障已提交 userId={} memoryId={} revision={}", userId, memoryId, mapper.revision(userId));
        return result(userId, requireOwned(userId, memoryId));
    }

    /**
     * 检索仅召回ID，再校验权威记录；不接收跨用户命中的正文。
     */
    @Transactional
    public List<MemoryView> search(String userId, String query, int limit) {
        if (query != null && query.length() > 200) {
            throw new RuntimeBusinessException(RuntimeExceptionCode.VALIDATION, "记忆检索词最多200字");
        }
        lockRevision(userId);
        LocalDateTime timestamp = now();
        return mapper.recall(userId, query, timestamp, Math.min(Math.max(limit, 1), 20)).stream()
                .map(id -> mapper.find(userId, id))
                .filter(row -> row != null && MemoryRules.active(row, timestamp))
                .map(this::view).toList();
    }

    public MemoryRow requireOwned(String userId, String memoryId) {
        MemoryRow memory = mapper.find(userId, memoryId);
        if (memory == null) {
            throw new RuntimeBusinessException(RuntimeExceptionCode.NOT_FOUND, "记忆不存在或无权访问");
        }
        return memory;
    }

    private void lockRevision(String userId) {
        mapper.ensureRevision(userId);
        mapper.lockRevision(userId);
    }

    private void invalidate(String userId) {
        mapper.advanceRevision(userId);
        mapper.blockOldScopes(userId, mapper.revision(userId));
    }

    private MemoryMutationResult result(String userId, MemoryRow memory) {
        int scopes = mapper.blockedScopes(userId);
        return new MemoryMutationResult(view(memory), mapper.revision(userId), scopes,
                scopes == 0 ? "已保存；后续新执行使用当前有效记忆" : "记忆已变更；旧执行上下文已阻塞，需从有效资料重新发起，已发出的请求不能撤回");
    }

    private MemoryView view(MemoryRow row) {
        String status = "ACTIVE".equals(row.status()) && !MemoryRules.active(row, now()) ? "EXPIRED" : row.status();
        return new MemoryView(row.memoryId(), row.key(), "DELETED".equals(status) ? "" : row.content(),
                "EXPLICIT", status, row.version(), row.expiresAt() == null ? null : row.expiresAt().atOffset(ZoneOffset.UTC), row.sourceMessageId());
    }

    private Long parseCursor(String cursor) {
        if (cursor == null || cursor.isBlank()) {
            return null;
        }
        try {
            long value = Long.parseLong(cursor);
            if (value <= 0) {
                throw new NumberFormatException();
            }
            return value;
        } catch (NumberFormatException exception) {
            throw new RuntimeBusinessException(RuntimeExceptionCode.VALIDATION, "记忆分页游标无效");
        }
    }

    private RuntimeBusinessException conflict(String message) {
        return new RuntimeBusinessException(RuntimeExceptionCode.CONFLICT, message);
    }

    private LocalDateTime utc(OffsetDateTime value) {
        return value == null ? null : value.withOffsetSameInstant(ZoneOffset.UTC).toLocalDateTime().truncatedTo(ChronoUnit.MILLIS);
    }

    private LocalDateTime now() {
        return LocalDateTime.now(ZoneOffset.UTC);
    }
}
