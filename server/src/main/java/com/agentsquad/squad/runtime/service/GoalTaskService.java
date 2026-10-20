package com.agentsquad.squad.runtime.service;

import com.agentsquad.squad.runtime.domain.*;
import com.agentsquad.squad.runtime.dto.*;
import com.agentsquad.squad.runtime.repository.*;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 用户目标受理服务。负责对话归属、幂等、任务及绑定、命令和首事件的一次事务提交。
 *
 * <p>模型或外部工具调用不在本事务内；后续 Bridge 从持久命令领取并调用 AgentScope。</p>
 */
@Service
@Slf4j
public class GoalTaskService {

    @Resource
    private ConversationMapper conversationMapper;

    @Resource
    private TaskMapper taskMapper;

    @Resource
    private BindingMapper bindingMapper;

    @Resource
    private CommandMapper commandMapper;

    @Resource
    private TaskEventMapper taskEventMapper;

    @Resource
    private ObjectMapper objectMapper;

    /**
     * 创建归属明确的对话，后续提交必须再锁该对话。
     */
    @Transactional
    public ConversationResponse createConversation(String userId, String title) {
        String conversationId = UUID.randomUUID().toString();
        conversationMapper.insert(conversationId, userId, title);
        log.info("对话创建成功 conversationId={} userId={}", conversationId, userId);
        return new ConversationResponse(conversationId, title, 0, "ACTIVE", 0, null);
    }

    /**
     * 按用户分页列出未删除对话；cursor 为存储主键 id 字符串，空表示首页。
     */
    public ConversationPage listConversations(String userId, String cursor, int limit) {
        Long cursorId = parseCursor(cursor);
        List<ConversationRow> rows = conversationMapper.listByOwner(userId, cursorId,
                Math.min(Math.max(limit, 1), 100));
        List<ConversationResponse> items = rows.stream().map(this::toResponse).toList();
        String nextCursor = rows.isEmpty() ? null : String.valueOf(rows.get(rows.size() - 1).id());
        return new ConversationPage(items, nextCursor);
    }

    /**
     * 读取当前用户单个对话快照。
     */
    public ConversationResponse getConversation(String userId, String conversationId) {
        ConversationRow row = conversationMapper.findOwned(userId, conversationId);
        if (row == null || "DELETED".equals(row.status())) {
            throw new RuntimeBusinessException(RuntimeExceptionCode.NOT_FOUND, "会话不存在");
        }
        return toResponse(row);
    }

    private ConversationResponse toResponse(ConversationRow row) {
        return new ConversationResponse(row.conversationId(), row.title(), row.generation(),
                row.status(), row.version(), row.activeTaskId());
    }

    private Long parseCursor(String cursor) {
        if (cursor == null || cursor.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(cursor);
        } catch (NumberFormatException exception) {
            throw new RuntimeBusinessException(RuntimeExceptionCode.VALIDATION, "分页游标无效");
        }
    }

    /**
     * 受理用户目标：锁定对话后先查幂等收据，再一次写入目标、绑定、命令、事件。
     *
     * <p>任何 SQL 失败都回滚；重试同键同输入返回原收据，不创建新任务。</p>
     */
    @Transactional
    public TaskReceipt submit(String userId, String conversationId, String idempotencyKey, String message) {
        if (idempotencyKey == null || idempotencyKey.isBlank() || idempotencyKey.length() > 160) {
            throw new RuntimeBusinessException(RuntimeExceptionCode.VALIDATION, "幂等键长度必须为 1 到 160");
        }
        if (message == null || message.isBlank() || message.length() > 20000) {
            throw new RuntimeBusinessException(RuntimeExceptionCode.VALIDATION, "目标内容长度必须为 1 到 20000");
        }
        ConversationRow conversation = conversationMapper.lockOwned(userId, conversationId);
        if (conversation == null || !"ACTIVE".equals(conversation.status())) {
            throw new RuntimeBusinessException(RuntimeExceptionCode.NOT_FOUND, "会话不存在");
        }
        String scopeKey = "task.create:" + conversationId;
        String payloadJson = toJson(Map.of("message", message));
        String payloadHash = sha256(payloadJson);
        CommandRow prior = commandMapper.findByKey(userId, scopeKey, idempotencyKey);
        if (prior != null) {
            if (!prior.payloadHash().equals(payloadHash)) {
                throw new RuntimeBusinessException(RuntimeExceptionCode.CONFLICT, "相同幂等键的输入不一致");
            }
            return new TaskReceipt(prior.taskId(), prior.commandId(), "QUEUED");
        }
        if (conversation.activeTaskId() != null) {
            throw new RuntimeBusinessException(RuntimeExceptionCode.CONFLICT, "会话已有未完成目标");
        }
        String taskId = UUID.randomUUID().toString();
        String bindingId = UUID.randomUUID().toString();
        String commandId = UUID.randomUUID().toString();
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        taskMapper.insert(taskId, userId, conversationId, payloadJson);
        bindingMapper.insertMaster(bindingId, userId, taskId, MasterAgentIdentity.AGENT_ID,
                UUID.randomUUID().toString());
        commandMapper.insertSubmit(commandId, userId, scopeKey, idempotencyKey, payloadHash,
                taskId, bindingId, payloadJson, now);
        taskEventMapper.insertAccepted(userId, taskId, "command:" + commandId,
                toJson(Map.of("taskId", taskId, "commandId", commandId)), now);
        if (conversationMapper.activateTask(userId, conversationId, taskId) != 1) {
            throw new RuntimeBusinessException(RuntimeExceptionCode.CONFLICT, "会话状态已变化");
        }
        log.info("目标受理成功 taskId={} commandId={} conversationId={}", taskId, commandId, conversationId);
        return new TaskReceipt(taskId, commandId, "QUEUED");
    }

    /**
     * 按登录主体读取目标，不暴露其他用户的存在性。
     */
    public TaskResponse findTask(String userId, String taskId) {
        TaskRow task = taskMapper.findOwned(userId, taskId);
        if (task == null) {
            throw new RuntimeBusinessException(RuntimeExceptionCode.NOT_FOUND, "目标不存在");
        }
        return toResponse(task);
    }

    /**
     * 列出某一会话下的目标，按创建时间倒序。
     */
    public GoalTaskPage listConversationTasks(String userId, String conversationId, String cursor, int limit) {
        Long cursorId = parseCursor(cursor);
        List<TaskRow> rows = taskMapper.listByConversation(userId, conversationId, cursorId,
                Math.min(Math.max(limit, 1), 100));
        List<TaskResponse> items = rows.stream().map(this::toResponse).toList();
        String nextCursor = rows.isEmpty() ? null : String.valueOf(rows.get(rows.size() - 1).id());
        return new GoalTaskPage(items, nextCursor);
    }

    private TaskResponse toResponse(TaskRow task) {
        return new TaskResponse(task.taskId(), task.conversationId(), task.status(), task.version(),
                formatTime(task.createTime()), formatTime(task.lastUpdateTime()));
    }

    private String formatTime(java.time.LocalDateTime time) {
        return time == null ? null : time.toString();
    }

    /**
     * 仅返回当前用户的命令收据。
     */
    public CommandResponse findCommand(String userId, String commandId) {
        CommandRow command = commandMapper.findOwned(userId, commandId);
        if (command == null) {
            throw new RuntimeBusinessException(RuntimeExceptionCode.NOT_FOUND, "命令不存在");
        }
        return new CommandResponse(command.commandId(), command.taskId(), command.status(),
                command.nativeTurnId(), command.nativeRunId(), command.nativeStatus());
    }

    /**
     * 从已提交事件按序号回放，客户端重连可从最后水位继续。
     */
    public List<TaskEventResponse> listEvents(String userId, String taskId, long after) {
        findTask(userId, taskId);
        return taskEventMapper.listAfter(userId, taskId, after).stream()
                .map(this::toEventResponse)
                .toList();
    }

    private TaskEventResponse toEventResponse(EventRow row) {
        return new TaskEventResponse(row.eventSeq(), row.taskId(), row.eventType(),
                row.payloadJson(), row.occurredAt());
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("运行输入无法序列化", exception);
        }
    }

    private String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("JVM 不支持 SHA-256", exception);
        }
    }
}
