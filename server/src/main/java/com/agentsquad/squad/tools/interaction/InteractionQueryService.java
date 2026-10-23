package com.agentsquad.squad.tools.interaction;

import com.agentsquad.squad.runtime.dto.Interaction;
import com.agentsquad.squad.runtime.dto.InteractionPage;
import com.agentsquad.squad.runtime.service.RuntimeBusinessException;
import com.agentsquad.squad.runtime.service.RuntimeExceptionCode;
import com.agentsquad.squad.runtime.util.RuntimePayloads;
import com.agentsquad.squad.tools.interaction.domain.InteractionRow;
import com.agentsquad.squad.tools.interaction.repository.InteractionMapper;
import com.agentsquad.squad.tools.operation.domain.OperationRow;
import com.agentsquad.squad.tools.operation.repository.OperationMapper;
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
    private InteractionMapper interactionMapper;

    @Resource
    private OperationMapper operationMapper;

    @Resource
    private ObjectMapper objectMapper;

    /**
     * 列出目标的交互存储行；游标为存储主键。
     */
    public List<InteractionRow> listByTask(String userId, String taskId, String cursor, int limit) {
        Long cursorId = parseCursor(cursor);
        return interactionMapper.listByTask(userId, taskId, cursorId, Math.min(Math.max(limit, 1), 100));
    }

    /**
     * 分页读取目标的交互视图；游标取最后一条交互的存储主键。
     */
    public InteractionPage listPage(String userId, String taskId, String cursor, int limit) {
        List<InteractionRow> rows = listByTask(userId, taskId, cursor, limit + 1);
        boolean hasMore = rows.size() > Math.min(Math.max(limit, 1), 100);
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
        return viewOf(userId, row);
    }

    /**
     * 列出目标仍开放的交互存储行（快照 pending 来源）。
     */
    public List<InteractionRow> listOpenByTask(String userId, String taskId) {
        return interactionMapper.findOpenIds(userId, taskId).stream()
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
                context.put("arguments", parseJsonOrNull(operation.argumentsJson()));
                context.put("businessIntentKey", operation.businessIntentKey());
                context.put("connectionId", operation.connectionId());
                context.put("connectionRevision", operation.connectionRevision());
                context.put("operationStatus", operation.status());
                approvalContext = context;
            }
        }
        return new Interaction(row.interactionId(), row.taskId(), row.jobId(), row.kind(),
                row.origin(), row.question(), parseJsonOrNull(row.answerSchemaJson()),
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
