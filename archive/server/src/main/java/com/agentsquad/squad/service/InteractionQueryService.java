package com.agentsquad.squad.service;

import com.agentsquad.squad.model.vo.InteractionVO;
import com.agentsquad.squad.model.vo.InteractionPageVO;
import com.agentsquad.squad.exceptions.RuntimeBusinessException;
import com.agentsquad.squad.enums.RuntimeExceptionCode;
import com.agentsquad.squad.utils.RuntimePayloads;
import com.agentsquad.squad.model.po.InteractionPO;
import com.agentsquad.squad.mapper.InteractionMapper;
import com.agentsquad.squad.model.po.OperationPO;
import com.agentsquad.squad.mapper.OperationMapper;
import com.agentsquad.squad.model.bo.OperationPreviewBO;
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
    private OperationPreviewBO operationPreview;

    /**
     * 列出目标的交互存储行；游标为存储主键。
     */
    public List<InteractionPO> listByTask(Long userId, Long taskId, String cursor, int limit) {
        scopeService.requireReadable(userId, scopeService.scopeForTask(userId, taskId));
        Long cursorId = parseCursor(cursor);
        return interactionMapper.listByTask(userId, taskId, cursorId, Math.min(Math.max(limit, 1), 100));
    }

    /**
     * 分页读取目标的交互视图；游标取最后一条交互的存储主键。
     */
    public InteractionPageVO listPage(Long userId, Long taskId, String cursor, int limit) {
        scopeService.requireReadable(userId, scopeService.scopeForTask(userId, taskId));
        int pageSize = Math.min(Math.max(limit, 1), 100);
        List<InteractionPO> rows = interactionMapper.listByTask(userId, taskId, parseCursor(cursor), pageSize + 1);
        boolean hasMore = rows.size() > pageSize;
        List<InteractionVO> items = rows.stream()
                .limit(Math.min(Math.max(limit, 1), 100))
                .map(row -> viewOf(userId, row))
                .toList();
        String nextCursor = null;
        if (hasMore) {
            Long lastCursorId = interactionMapper.cursorIdOf(userId,
                    rows.get(Math.min(Math.max(limit, 1), 100) - 1).getInteractionId());
            nextCursor = lastCursorId == null ? null : String.valueOf(lastCursorId);
        }
        return new InteractionPageVO(items, nextCursor);
    }

    /**
     * 读取当前用户单个交互视图；不存在统一按 NOT_FOUND 处理。
     */
    public InteractionVO getOwned(Long userId, Long interactionId) {
        InteractionPO row = interactionMapper.findOwned(userId, interactionId);
        if (row == null) {
            throw new RuntimeBusinessException(RuntimeExceptionCode.NOT_FOUND, "交互不存在");
        }
        scopeService.requireReadable(userId, row.getScopeId());
        return viewOf(userId, row);
    }

    /**
     * 列出目标仍开放的交互存储行（快照 pending 来源）。
     */
    public List<InteractionPO> listOpenByTask(Long userId, Long taskId) {
        scopeService.requireReadable(userId, scopeService.scopeForTask(userId, taskId));
        return interactionMapper.findOpenIds(userId, taskId).stream()
                .map(id -> interactionMapper.findOwned(userId, id))
                .filter(row -> row != null && "OPEN".equals(row.getStatus()))
                .toList();
    }

    public List<InteractionPO> listOpenByScope(Long userId, Long scopeId) {
        scopeService.requireReadable(userId, scopeId);
        return interactionMapper.findOpenByScope(userId, scopeId).stream()
                .map(id -> interactionMapper.findOwned(userId, id))
                .filter(row -> row != null && "OPEN".equals(row.getStatus()))
                .toList();
    }

    /**
     * 把存储行映射为对外视图；审批卡附带账本操作上下文供核对与确认摘要比对。
     */
    public InteractionVO viewOf(Long userId, InteractionPO row) {
        Object approvalContext = null;
        if (KIND_APPROVAL.equals(row.getKind()) && row.getOperationId() != null) {
            OperationPO operation = operationMapper.findOwned(userId, row.getOperationId());
            if (operation != null) {
                Map<String, Object> context = new LinkedHashMap<>();
                context.put("operationId", String.valueOf(operation.getOperationId()));
                context.put("toolKey", operation.getToolKey());
                context.put("effect", operation.getEffect());
                context.put("argumentsPreview", operationPreview.arguments(operation.getArgumentsJson()));
                context.put("resourceSummary", operation.getToolKey() + " · " + operation.getEffect());
                context.put("requestHash", operation.getRequestHash());
                approvalContext = context;
            }
        }
        return new InteractionVO(row.getInteractionId(), row.getTaskId(), row.getJobId(), row.getKind(),
                row.getOrigin(), KIND_APPROVAL.equals(row.getKind()) ? "请确认此操作的影响后决定是否执行" : row.getQuestion(),
                parseJsonOrNull(row.getAnswerSchemaJson()),
                row.getRequestHash(), row.getPlanVersion(),
                row.getExpiresAt() == null ? null : row.getExpiresAt().atOffset(java.time.ZoneOffset.UTC).toString(),
                row.getStatus(), row.getVersion(), approvalContext);
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
