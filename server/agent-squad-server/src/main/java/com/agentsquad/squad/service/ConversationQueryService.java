package com.agentsquad.squad.service;

import com.agentsquad.squad.domain.response.ActivityHistoryPageResponse;
import com.agentsquad.squad.domain.request.ActivityHistoryRequest;
import com.agentsquad.squad.domain.dto.ConversationEvent;
import com.agentsquad.squad.domain.response.ConversationSnapshotResponse;
import com.agentsquad.squad.domain.response.ConversationViewResponse;
import com.agentsquad.squad.domain.dto.ExecutionActivityView;
import com.agentsquad.squad.domain.dto.MessageView;
import com.agentsquad.squad.domain.dto.ResponseExecutionView;
import com.agentsquad.squad.domain.dto.TaskDirectiveView;
import com.agentsquad.squad.domain.dto.TaskResponse;

import com.agentsquad.squad.domain.entity.ConversationExecutionDO;
import com.agentsquad.squad.common.exception.RuntimeBusinessException;
import com.agentsquad.squad.common.exception.RuntimeExceptionCode;
import com.agentsquad.squad.common.util.RuntimePayloads;
import com.agentsquad.squad.domain.entity.ConversationHeaderDO;
import com.agentsquad.squad.domain.enums.ResponseExecutionStatus;
import com.agentsquad.squad.domain.dto.ConversationEvent;
import com.agentsquad.squad.domain.response.ActivityHistoryPageResponse;
import com.agentsquad.squad.domain.request.ActivityHistoryRequest;
import com.agentsquad.squad.domain.dto.ExecutionActivityView;
import com.agentsquad.squad.mapper.ConversationReadMapper;
import com.agentsquad.squad.mapper.ResponseContextMapper;
import com.agentsquad.squad.mapper.TaskMapper;
import com.agentsquad.squad.domain.dto.TaskResponse;
import com.agentsquad.squad.mapper.ArtifactMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 用户会话的一致前缀读取服务。
 * <p>
 * 同一可重复读事务中读取消息、状态和水位，历史翻页只读不派发；游标绑定用户、会话及代际。
 */
@Service
public class ConversationQueryService {
    private static final int ACTIVITY_HISTORY_PAGE_SIZE = 40;

    @Resource
    private ExecutionFailureService failures;

    @Resource
    private ConversationReadMapper reads;

    @Resource
    private ResponseContextMapper material;

    @Resource
    private ArtifactMapper artifactMapper;

    @Resource
    private ArtifactService artifacts;

    @Resource
    private InteractionQueryService interactions;

    @Resource
    private TaskMapper tasks;

    @Resource
    private com.agentsquad.squad.mapper.TaskDirectiveMapper directives;

    @Resource
    private ObjectMapper json;

    @Resource
    private com.agentsquad.squad.mapper.ExecutionActivityMapper activities;

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public ConversationSnapshotResponse snapshot(String userId, String conversationId, Long before, int limit) {
        if ((before != null && before < 1) || limit < 1 || limit > 100) {
            throw invalid("历史分页参数无效");
        }
        ConversationHeaderDO header = requireHeader(userId, conversationId);
        var rows = reads.messages(userId, conversationId, before, limit + 1);
        boolean more = rows.size() > limit;
        var page = new ArrayList<>(rows.subList(0, Math.min(rows.size(), limit)));
        Collections.reverse(page);
        List<MessageView> messages = page.stream().map(row -> new MessageView(row.messageId(), row.sequence(), row.role(), row.text(), row.revision(), row.completed(), row.sourceMessageId(), row.executionId(), row.taskId(), row.occurredAt().atOffset(java.time.ZoneOffset.UTC).toString(),
                material.artifactIds(userId, row.messageId()).stream().filter(id -> artifactMapper.findOwned(userId, id) != null).map(id -> artifacts.get(userId, id)).toList())).toList();
        TaskResponse active = null;
        if (header.activeTaskId() != null) {
            var task = tasks.findOwned(userId, header.activeTaskId());
            if (task != null) {
                active = new TaskResponse(task.taskId(), task.conversationId(), task.status(), task.version(), task.planVersion(), task.answerRevision(), task.cancelRequestedAt() == null ? null : task.cancelRequestedAt().atOffset(java.time.ZoneOffset.UTC).toString(), null, task.createTime().atOffset(java.time.ZoneOffset.UTC).toString(), task.lastUpdateTime().atOffset(java.time.ZoneOffset.UTC).toString());
            }
        }
        return new ConversationSnapshotResponse(ConversationViewResponse.from(header), messages, more ? page.getFirst().sequence() : null,
                reads.activeExecutions(userId, conversationId).stream().map(row -> executionView(userId, row)).toList(), active,
                reads.interactionIds(userId, conversationId).stream().map(id -> interactions.getOwned(userId, id)).toList(),
                reads.deliverableIds(userId, conversationId).stream().filter(id -> artifactMapper.findOwned(userId, id) != null).map(id -> artifacts.get(userId, id)).toList(), failures.list(userId, conversationId), reads.uncertainOperationIds(userId, conversationId),
                page.isEmpty() ? List.of() : reads.pageExecutions(userId, conversationId, page.getFirst().sequence(), page.getLast().sequence()).stream().map(row -> executionView(userId, row)).toList(),
                page.isEmpty() ? List.of() : reads.pageTasks(userId, conversationId, page.getFirst().sequence(), page.getLast().sequence()),
                page.isEmpty() ? List.of() : directives.pageInConversation(userId, conversationId, header.generation(),
                        page.getFirst().sequence(), page.getLast().sequence()).stream()
                        .map(TaskDirectiveView::from).toList(),
                activities.recent(userId, conversationId, header.generation(), 100).stream()
                        .map(ExecutionActivityView::from).toList(),
                cursor(header, header.eventSequence()));
    }

    /** 仅查询当前会话代际的来源消息，按固定活动身份向前翻页。 */
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public ActivityHistoryPageResponse activityHistory(String userId, String conversationId, ActivityHistoryRequest query) {
        var header = requireHeader(userId, conversationId);
        if (query.sourceMessageId() == null || query.sourceMessageId().isBlank() || query.sourceMessageId().length() > 36
                || query.beforeActivityId() != null && (query.beforeActivityId().isBlank() || query.beforeActivityId().length() > 36)) {
            throw new RuntimeBusinessException(RuntimeExceptionCode.VALIDATION, "活动历史查询参数无效");
        }
        var rows = activities.history(userId, conversationId, header.generation(), query, ACTIVITY_HISTORY_PAGE_SIZE + 1);
        var items = rows.stream().limit(ACTIVITY_HISTORY_PAGE_SIZE).map(ExecutionActivityView::from).toList();
        String nextCursor = rows.size() > ACTIVITY_HISTORY_PAGE_SIZE ? items.getLast().activityId() : null;
        return new ActivityHistoryPageResponse(items, nextCursor);
    }
    /**
     * 每次读取同时重验会话归属、代际和保留水位；删除后的既有订阅也不能继续读取内容。
     */
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public List<ConversationEvent> events(String userId, String conversationId, String after) {
        var header = requireHeader(userId, conversationId);
        long sequence = validateCursor(userId, header, after);
        return reads.events(userId, conversationId, header.generation(), sequence).stream().map(row -> {
            try {
                return new ConversationEvent(conversationId, row.generation(), row.sequence(), row.type(), row.occurredAt().atOffset(java.time.ZoneOffset.UTC).toString(), RuntimePayloads.readJson(json, row.payloadJson()));
            } catch (com.fasterxml.jackson.core.JsonProcessingException exception) {
                throw new IllegalStateException("公开会话事件无法读取", exception);
            }
        }).toList();
    }

    public String eventCursor(ConversationEvent event) {
        return event.conversationId() + ":" + event.generation() + ":" + event.sequence();
    }

    public ConversationHeaderDO requireHeader(String userId, String conversationId) {
        var header = reads.header(userId, conversationId);
        if (header == null) {
            throw new RuntimeBusinessException(RuntimeExceptionCode.NOT_FOUND, "对话不存在或已删除");
        }
        return header;
    }

    public long validateCursor(String userId, ConversationHeaderDO header, String after) {
        long sequence = 0;
        if (after != null && !after.isBlank()) {
            if (after.length() > 200) {
                throw invalid("会话游标过长");
            }
            String[] parts = after.split(":", -1);
            if (parts.length != 3 || !header.conversationId().equals(parts[0])) {
                throw invalid("会话游标不匹配");
            }
            try {
                long generation = Long.parseLong(parts[1]);
                sequence = Long.parseLong(parts[2]);
                if (generation < 0 || generation > header.generation()) {
                    throw invalid("会话游标代际无效");
                }
                if (generation < header.generation()) {
                    throw new RuntimeBusinessException(RuntimeExceptionCode.EXPIRED, "会话游标已过期，请重新读取对话");
                }
            } catch (NumberFormatException exception) {
                throw invalid("会话游标无效");
            }
        }
        if (sequence < 0 || sequence > header.eventSequence()) {
            throw invalid("会话游标超出当前水位");
        }
        Long first = reads.firstEvent(userId, header.conversationId(), header.generation());
        if (first != null && sequence < first - 1) {
            throw new RuntimeBusinessException(RuntimeExceptionCode.EXPIRED, "会话事件已超出保留范围，请重新读取对话");
        }
        return sequence;
    }

    private ResponseExecutionView executionView(String userId, ConversationExecutionDO row) {
        boolean terminal = java.util.Set.of("SUCCEEDED", "PARTIAL", "FAILED", "CANCELLED").contains(row.status());
        return new ResponseExecutionView(row.executionId(), row.sourceMessageId(), ResponseExecutionStatus.valueOf(row.status()), row.version(),
                row.answerMessageId(), row.failureId(), "FAILED".equals(row.status()) && failures.canRetry(userId, row.executionId()) ? List.of("RETRY")
                : row.cancellationRequested() || terminal || "CANCELLING".equals(row.status()) ? List.of() : List.of("STOP"));
    }

    private String cursor(ConversationHeaderDO header, long sequence) {
        return header.conversationId() + ":" + header.generation() + ":" + sequence;
    }

    private RuntimeBusinessException invalid(String message) {
        return new RuntimeBusinessException(RuntimeExceptionCode.VALIDATION, message);
    }
}
