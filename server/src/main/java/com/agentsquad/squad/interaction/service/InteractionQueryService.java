package com.agentsquad.squad.interaction.service;

import com.agentsquad.squad.presentation.dto.Interaction;
import com.agentsquad.squad.conversation.service.ExecutionScopeService;
import com.agentsquad.squad.presentation.dto.InteractionPage;
import com.agentsquad.squad.common.exception.RuntimeBusinessException;
import com.agentsquad.squad.common.exception.RuntimeExceptionCode;
import com.agentsquad.squad.common.util.RuntimePayloads;
import com.agentsquad.squad.interaction.domain.InteractionRow;
import com.agentsquad.squad.interaction.repository.InteractionMapper;
import com.agentsquad.squad.tools.operation.domain.OperationRow;
import com.agentsquad.squad.tools.operation.repository.OperationMapper;
import com.agentsquad.squad.tools.operation.OperationPreview;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 持久交互只读查询与视图映射；不依赖投递开关，供快照和交互查询接口使用。
 *
 * <p>审批卡附带账本操作上下文供核对与确认摘要比对；答复与派发在 InteractionService。</p>
 */
@Service
@Slf4j
public class InteractionQueryService {

    private static final String KIND_APPROVAL = "APPROVAL";

    @Resource
    private ExecutionScopeService scopeService;

    @Resource
    private InteractionMapper interactionMapper;

    @Resource
    private OperationMapper operationMapper;

    @Resource
    private ObjectMapper objectMapper;

    @Resource
    private OperationPreview operationPreview;

    /**
     * 列出目标的交互存储行；游标为存储主键。
     */
    public List<InteractionRow> listByTask(String userId, String taskId, String cursor, int limit) {
        scopeService.requireReadable(userId, scopeService.scopeForTask(userId, taskId));
        Long cursorId = parseCursor(cursor);
        return interactionMapper.listByTask(userId, taskId, cursorId, Math.min(Math.max(limit, 1), 100));
    }

    /**
     * 分页读取目标的交互视图；游标取最后一条交互的存储主键。
     */
    public InteractionPage listPage(String userId, String taskId, String cursor, int limit) {
        scopeService.requireReadable(userId, scopeService.scopeForTask(userId, taskId));
        int pageSize = Math.min(Math.max(limit, 1), 100);
        List<InteractionRow> rows = interactionMapper.listByTask(userId, taskId, parseCursor(cursor), pageSize + 1);
        boolean hasMore = rows.size() > pageSize;
        List<Interaction> items = rows.stream()
                .limit(Math.min(Math.max(limit, 1), 100))
                .map(row -> viewOf(userId, row))
                .toList();
        String nextCursor = null;
        if (hasMore) {
            Long lastCursorId = interactionMapper.cursorIdOf(userId,
                    rows.get(Math.min(Math.max(limit, 1), 100) - 1).interactionId());
            nextCursor = lastCursorId == null ? null : String.valueOf(lastCursorId);
        }
        return new InteractionPage(items, nextCursor);
    }

    /**
     * 读取当前用户单个交互视图；不存在统一按 NOT_FOUND 处理。
     */
    public Interaction getOwned(String userId, String interactionId) {
        InteractionRow row = interactionMapper.findOwned(userId, interactionId);
        if (row == null) {
            throw new RuntimeBusinessException(RuntimeExceptionCode.NOT_FOUND, "交互不存在");
        }
        scopeService.requireReadable(userId, row.scopeId());
        return viewOf(userId, row);
    }

    /**
     * 列出目标仍开放的交互存储行（快照 pending 来源）。
     */
    public List<InteractionRow> listOpenByTask(String userId, String taskId) {
        scopeService.requireReadable(userId, scopeService.scopeForTask(userId, taskId));
        return interactionMapper.findOpenIds(userId, taskId).stream()
                .map(id -> interactionMapper.findOwned(userId, id))
                .filter(row -> row != null && "OPEN".equals(row.status()))
                .toList();
    }

    public List<InteractionRow> listOpenByScope(String userId, String scopeId) {
        scopeService.requireReadable(userId, scopeId);
        return interactionMapper.findOpenByScope(userId, scopeId).stream()
                .map(id -> interactionMapper.findOwned(userId, id))
                .filter(row -> row != null && "OPEN".equals(row.status()))
                .toList();
    }

    /**
     * 把存储行映射为对外视图；审批卡附带账本操作上下文供核对与确认摘要比对。
     */
    public Interaction viewOf(String userId, InteractionRow row) {
        Object approvalContext = null;
        if (KIND_APPROVAL.equals(row.kind()) && row.operationId() != null) {
            OperationRow operation = operationMapper.findOwned(userId, row.operationId());
            if (operation != null) {
                Map<String, Object> context = new LinkedHashMap<>();
                context.put("operationId", operation.operationId());
                context.put("toolKey", operation.toolKey());
                context.put("effect", operation.effect());
                context.put("argumentsPreview", operationPreview.arguments(operation.argumentsJson()));
                context.put("resourceSummary", operation.toolKey() + " · " + operation.effect());
                context.put("requestHash", operation.requestHash());
                approvalContext = context;
            }
        }
        return new Interaction(row.interactionId(), row.taskId(), row.jobId(), row.kind(),
                row.origin(), KIND_APPROVAL.equals(row.kind()) ? "请确认此操作的影响后决定是否执行" : row.question(),
                parseJsonOrNull(row.answerSchemaJson()),
                row.requestHash(), row.planVersion(),
                row.expiresAt() == null ? null : row.expiresAt().toString(),
                row.status(), row.version(), approvalContext);
    }

    private Object parseJsonOrNull(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return RuntimePayloads.readJson(objectMapper, json);
        } catch (Exception exception) {
            log.warn("交互载荷无法解析，按空视图返回");
            return null;
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
}
