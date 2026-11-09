package com.agentsquad.squad.service;

import com.agentsquad.squad.mapper.ExecutionActivityMapper;
import com.agentsquad.squad.mapper.TaskDirectiveMapper;
import com.agentsquad.squad.model.vo.ActivityHistoryPageVO;
import com.agentsquad.squad.model.dto.ActivityHistoryDTO;
import com.agentsquad.squad.model.bo.ConversationEventBO;
import com.agentsquad.squad.model.vo.ConversationSnapshotVO;
import com.agentsquad.squad.model.vo.ConversationViewVO;
import com.agentsquad.squad.model.vo.ExecutionActivityVO;
import com.agentsquad.squad.model.vo.MessageVO;
import com.agentsquad.squad.model.vo.ResponseExecutionVO;
import com.agentsquad.squad.model.vo.TaskDirectiveVO;
import com.agentsquad.squad.model.vo.TaskVO;

import com.agentsquad.squad.model.bo.ConversationExecutionBO;
import com.agentsquad.squad.exceptions.RuntimeBusinessException;
import com.agentsquad.squad.enums.RuntimeExceptionCode;
import com.agentsquad.squad.utils.RuntimePayloads;
import com.agentsquad.squad.model.bo.ConversationHeaderBO;
import com.agentsquad.squad.enums.ResponseExecutionStatus;
import com.agentsquad.squad.mapper.ConversationReadMapper;
import com.agentsquad.squad.mapper.ResponseContextMapper;
import com.agentsquad.squad.mapper.TaskMapper;
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
    private TaskDirectiveMapper directives;

    @Resource
    private ObjectMapper json;

    @Resource
    private ExecutionActivityMapper activities;

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public ConversationSnapshotVO snapshot(String userId, String conversationId, Long before, int limit) {
        if ((before != null && before < 1) || limit < 1 || limit > 100) {
            throw invalid("历史分页参数无效");
        }
        ConversationHeaderBO header = requireHeader(userId, conversationId);
        var rows = reads.messages(userId, conversationId, before, limit + 1);
        boolean more = rows.size() > limit;
        var page = new ArrayList<>(rows.subList(0, Math.min(rows.size(), limit)));
        Collections.reverse(page);
        List<MessageVO> messages = page.stream().map(row -> new MessageVO(row.getMessageId(), row.getSequence(), row.getRole(), row.getText(), row.getRevision(), row.isCompleted(), row.getSourceMessageId(), row.getExecutionId(), row.getTaskId(), row.getOccurredAt().atOffset(java.time.ZoneOffset.UTC).toString(),
                material.artifactIds(userId, row.getMessageId()).stream().filter(id -> artifactMapper.findOwned(userId, id) != null).map(id -> artifacts.get(userId, id)).toList())).toList();
        TaskVO active = null;
        if (header.getActiveTaskId() != null) {
            var task = tasks.findOwned(userId, header.getActiveTaskId());
            if (task != null) {
                active = new TaskVO(task.getTaskId(), task.getConversationId(), task.getStatus(), task.getVersion(), task.getPlanVersion(), task.getAnswerRevision(), task.getCancelRequestedAt() == null ? null : task.getCancelRequestedAt().atOffset(java.time.ZoneOffset.UTC).toString(), null, task.getCreateTime().atOffset(java.time.ZoneOffset.UTC).toString(), task.getLastUpdateTime().atOffset(java.time.ZoneOffset.UTC).toString());
            }
        }
        return new ConversationSnapshotVO(ConversationViewVO.from(header), messages, more ? page.getFirst().getSequence() : null,
                reads.activeExecutions(userId, conversationId).stream().map(row -> executionView(userId, row)).toList(), active,
                reads.interactionIds(userId, conversationId).stream().map(id -> interactions.getOwned(userId, id)).toList(),
                reads.deliverableIds(userId, conversationId).stream().filter(id -> artifactMapper.findOwned(userId, id) != null).map(id -> artifacts.get(userId, id)).toList(), failures.list(userId, conversationId), reads.uncertainOperationIds(userId, conversationId),
                page.isEmpty() ? List.of() : reads.pageExecutions(userId, conversationId, page.getFirst().getSequence(), page.getLast().getSequence()).stream().map(row -> executionView(userId, row)).toList(),
                page.isEmpty() ? List.of() : reads.pageTasks(userId, conversationId, page.getFirst().getSequence(), page.getLast().getSequence()),
                page.isEmpty() ? List.of() : directives.pageInConversation(userId, conversationId, header.getGeneration(),
                                page.getFirst().getSequence(), page.getLast().getSequence()).stream()
                        .map(TaskDirectiveVO::from).toList(),
                activities.recent(userId, conversationId, header.getGeneration(), 100).stream()
                        .map(ExecutionActivityVO::from).toList(),
                cursor(header, header.getEventSequence()));
    }

    /**
     * 仅查询当前会话代际的来源消息，按固定活动身份向前翻页。
     */
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public ActivityHistoryPageVO activityHistory(String userId, String conversationId, ActivityHistoryDTO query) {
        var header = requireHeader(userId, conversationId);
        if (query.sourceMessageId() == null || query.sourceMessageId().isBlank() || query.sourceMessageId().length() > 36
                || query.beforeActivityId() != null && (query.beforeActivityId().isBlank() || query.beforeActivityId().length() > 36)) {
            throw new RuntimeBusinessException(RuntimeExceptionCode.VALIDATION, "活动历史查询参数无效");
        }
        var rows = activities.history(userId, conversationId, header.getGeneration(), query, ACTIVITY_HISTORY_PAGE_SIZE + 1);
        var items = rows.stream().limit(ACTIVITY_HISTORY_PAGE_SIZE).map(ExecutionActivityVO::from).toList();
        String nextCursor = rows.size() > ACTIVITY_HISTORY_PAGE_SIZE ? items.getLast().activityId() : null;
        return new ActivityHistoryPageVO(items, nextCursor);
    }

    /**
     * 每次读取同时重验会话归属、代际和保留水位；删除后的既有订阅也不能继续读取内容。
     */
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public List<ConversationEventBO> events(String userId, String conversationId, String after) {
        var header = requireHeader(userId, conversationId);
        long sequence = validateCursor(userId, header, after);
        return reads.events(userId, conversationId, header.getGeneration(), sequence).stream().map(row -> {
            try {
                return new ConversationEventBO(conversationId, row.getGeneration(), row.getSequence(), row.getType(), row.getOccurredAt().atOffset(java.time.ZoneOffset.UTC).toString(), RuntimePayloads.readJson(json, row.getPayloadJson()));
            } catch (com.fasterxml.jackson.core.JsonProcessingException exception) {
                throw new IllegalStateException("公开会话事件无法读取", exception);
            }
        }).toList();
    }

    public String eventCursor(ConversationEventBO event) {
        return event.conversationId() + ":" + event.generation() + ":" + event.sequence();
    }

    public ConversationHeaderBO requireHeader(String userId, String conversationId) {
        var header = reads.header(userId, conversationId);
        if (header == null) {
            throw new RuntimeBusinessException(RuntimeExceptionCode.NOT_FOUND, "对话不存在或已删除");
        }
        return header;
    }

    public long validateCursor(String userId, ConversationHeaderBO header, String after) {
        long sequence = 0;
        if (after != null && !after.isBlank()) {
            if (after.length() > 200) {
                throw invalid("会话游标过长");
            }
            String[] parts = after.split(":", -1);
            if (parts.length != 3 || !header.getConversationId().equals(parts[0])) {
                throw invalid("会话游标不匹配");
            }
            try {
                long generation = Long.parseLong(parts[1]);
                sequence = Long.parseLong(parts[2]);
                if (generation < 0 || generation > header.getGeneration()) {
                    throw invalid("会话游标代际无效");
                }
                if (generation < header.getGeneration()) {
                    throw new RuntimeBusinessException(RuntimeExceptionCode.EXPIRED, "会话游标已过期，请重新读取对话");
                }
            } catch (NumberFormatException exception) {
                throw invalid("会话游标无效");
            }
        }
        if (sequence < 0 || sequence > header.getEventSequence()) {
            throw invalid("会话游标超出当前水位");
        }
        Long first = reads.firstEvent(userId, header.getConversationId(), header.getGeneration());
        if (first != null && sequence < first - 1) {
            throw new RuntimeBusinessException(RuntimeExceptionCode.EXPIRED, "会话事件已超出保留范围，请重新读取对话");
        }
        return sequence;
    }

    private ResponseExecutionVO executionView(String userId, ConversationExecutionBO row) {
        boolean terminal = java.util.Set.of("SUCCEEDED", "PARTIAL", "FAILED", "CANCELLED").contains(row.getStatus());
        return new ResponseExecutionVO(row.getExecutionId(), row.getSourceMessageId(), ResponseExecutionStatus.valueOf(row.getStatus()), row.getVersion(),
                row.getAnswerMessageId(), row.getFailureId(), "FAILED".equals(row.getStatus()) && failures.canRetry(userId, row.getExecutionId()) ? List.of("RETRY")
                : row.isCancellationRequested() || terminal || "CANCELLING".equals(row.getStatus()) ? List.of() : List.of("STOP"));
    }

    private String cursor(ConversationHeaderBO header, long sequence) {
        return header.getConversationId() + ":" + header.getGeneration() + ":" + sequence;
    }

    private RuntimeBusinessException invalid(String message) {
        return new RuntimeBusinessException(RuntimeExceptionCode.VALIDATION, message);
    }
}
