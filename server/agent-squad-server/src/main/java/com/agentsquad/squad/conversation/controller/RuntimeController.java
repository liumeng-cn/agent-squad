package com.agentsquad.squad.conversation.controller;

import com.agentsquad.squad.common.exception.RuntimeBusinessException;
import com.agentsquad.squad.common.exception.RuntimeExceptionCode;

import com.agentsquad.squad.identity.UserContext;
import jakarta.servlet.http.HttpServletRequest;
import com.agentsquad.squad.conversation.dto.CommandReceipt;
import com.agentsquad.squad.conversation.dto.CommandResponse;
import com.agentsquad.squad.conversation.dto.ControlRequest;
import com.agentsquad.squad.conversation.dto.ConversationCreateRequest;
import com.agentsquad.squad.conversation.dto.ConversationPage;
import com.agentsquad.squad.conversation.dto.ConversationResponse;
import com.agentsquad.squad.conversation.dto.GoalTaskPage;
import com.agentsquad.squad.conversation.dto.TaskCreateRequest;
import com.agentsquad.squad.conversation.dto.TaskReceipt;
import com.agentsquad.squad.conversation.dto.TaskResponse;
import com.agentsquad.squad.presentation.dto.TaskSnapshot;
import com.agentsquad.squad.presentation.dto.WorkerJobPage;
import com.agentsquad.squad.conversation.service.ConversationDeletionService;
import com.agentsquad.squad.conversation.service.GoalTaskService;
import com.agentsquad.squad.conversation.service.TaskControlService;
import com.agentsquad.squad.presentation.service.TaskEventStreamer;
import com.agentsquad.squad.presentation.service.TaskSnapshotService;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 目标受理和查询 HTTP 边界。用户归属只取认证会话，不接受可伪造的身份请求头。
 *
 * <p>Controller 负责参数和协议；事务、幂等、归属与持久事件由 GoalTaskService 负责。</p>
 */
@RestController
@RequestMapping("/api/v1")
public class RuntimeController {

    @Resource
    private com.agentsquad.squad.conversation.service.ConversationTitleService conversationTitles;

    @Resource
    private GoalTaskService goalTaskService;

    @Resource
    private ConversationDeletionService conversationDeletionService;

    @Resource
    private TaskControlService taskControlService;

    @Resource
    private TaskEventStreamer taskEventStreamer;

    @Resource
    private TaskSnapshotService taskSnapshotService;

    /**
     * 查询当前计划的归属子任务；不把 Worker 当作可由用户独立启动的目标。
     */
    @GetMapping("/tasks/{taskId}/jobs")
    public WorkerJobPage listJobs(HttpServletRequest servletRequest,
                                  @PathVariable String taskId) {
        return taskSnapshotService.listJobs(UserContext.current(servletRequest).userId(), taskId);
    }

    /**
     * 创建当前用户的对话。
     */
    @PostMapping("/conversations")
    @ResponseStatus(HttpStatus.CREATED)
    public ConversationResponse createConversation(
            HttpServletRequest servletRequest,
            @Valid @RequestBody ConversationCreateRequest request) {
        return goalTaskService.createConversation(UserContext.current(servletRequest).userId(), request.title());
    }

    /**
     * 列出当前用户的会话，cursor 为存储主键 id。
     */
    @GetMapping("/conversations")
    public com.agentsquad.squad.presentation.dto.ConversationListPage listConversations(
            HttpServletRequest servletRequest,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "20") int limit) {
        return conversationTitles.list(UserContext.current(servletRequest).userId(), cursor, limit);
    }

    /**
     * 读取当前用户单个会话快照。
     */
    @GetMapping("/conversations/{conversationId}")
    public ConversationResponse getConversation(
            HttpServletRequest servletRequest,
            @PathVariable String conversationId) {
        return goalTaskService.getConversation(UserContext.current(servletRequest).userId(), conversationId);
    }

    /**
     * 受理删除当前用户会话；202 仅表示删除已持久受理，清理异步完成。
     */
    @DeleteMapping("/conversations/{conversationId}")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void deleteConversation(
            HttpServletRequest servletRequest,
            @PathVariable String conversationId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestHeader("If-Match") String ifMatch) {
        conversationDeletionService.delete(UserContext.current(servletRequest).userId(),
                conversationId, idempotencyKey, parseVersion(ifMatch));
    }

    private long parseVersion(String ifMatch) {
        if (ifMatch == null || !ifMatch.matches("[0-9]+")) {
            throw new RuntimeBusinessException(RuntimeExceptionCode.VALIDATION, "If-Match 必须是十进制资源版本");
        }
        try {
            return Long.parseLong(ifMatch);
        } catch (NumberFormatException exception) {
            throw new RuntimeBusinessException(RuntimeExceptionCode.VALIDATION, "If-Match 版本超出范围");
        }
    }

    /**
     * 列出一个会话下的目标。
     */
    @GetMapping("/conversations/{conversationId}/tasks")
    public GoalTaskPage listConversationTasks(
            HttpServletRequest servletRequest,
            @PathVariable String conversationId,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "20") int limit) {
        return goalTaskService.listConversationTasks(UserContext.current(servletRequest).userId(), conversationId, cursor, limit);
    }

    /**
     * 按 Idempotency-Key 受理目标，202 仅表示平台事务已提交。
     */
    @PostMapping("/conversations/{conversationId}/tasks")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public TaskReceipt createTask(
            HttpServletRequest servletRequest,
            @PathVariable String conversationId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody TaskCreateRequest request) {
        return goalTaskService.submit(UserContext.current(servletRequest).userId(),
                conversationId, idempotencyKey, request.message(), request.artifactIds());
    }

    /**
     * 查询当前用户的目标状态。
     */
    @GetMapping("/tasks/{taskId}")
    public TaskResponse getTask(HttpServletRequest servletRequest,
                                @PathVariable String taskId) {
        return goalTaskService.findTask(UserContext.current(servletRequest).userId(), taskId);
    }

    /**
     * 查询当前用户的目标详情快照。
     */
    @GetMapping("/tasks/{taskId}/snapshot")
    public TaskSnapshot getTaskSnapshot(HttpServletRequest servletRequest,
                                        @PathVariable String taskId) {
        return taskSnapshotService.snapshot(UserContext.current(servletRequest).userId(), taskId);
    }

    /**
     * 取消当前用户的未结束目标。
     */
    @PostMapping("/tasks/{taskId}/cancel")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public CommandReceipt cancelTask(
            HttpServletRequest servletRequest,
            @PathVariable String taskId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody ControlRequest request) {
        return taskControlService.cancel(UserContext.current(servletRequest).userId(),
                taskId, idempotencyKey, request.expectedVersion());
    }

    /**
     * 恢复当前用户的可恢复目标。
     */
    @PostMapping("/tasks/{taskId}/resume")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public CommandReceipt resumeTask(
            HttpServletRequest servletRequest,
            @PathVariable String taskId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody ControlRequest request) {
        return taskControlService.resume(UserContext.current(servletRequest).userId(),
                taskId, idempotencyKey, request.expectedVersion());
    }

    /**
     * 以 SSE 方式订阅当前用户的任务公开事件；首次可传 after，重连以 Last-Event-ID 为准。
     */
    @GetMapping(value = "/tasks/{taskId}/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter observeEvents(
            HttpServletRequest servletRequest,
            @PathVariable String taskId,
            @RequestHeader(value = "Last-Event-ID", required = false) String lastEventId,
            @RequestParam(required = false) String after) {
        return taskEventStreamer.stream(UserContext.current(servletRequest).userId(), taskId, resolveEventCursor(taskId, lastEventId, after));
    }

    private long resolveEventCursor(String taskId, String lastEventId, String after) {
        if (lastEventId != null && !lastEventId.isBlank()) {
            int separator = lastEventId.lastIndexOf(':');
            if (separator <= 0 || separator == lastEventId.length() - 1) {
                throw new RuntimeBusinessException(RuntimeExceptionCode.VALIDATION, "Last-Event-ID 格式无效");
            }
            String cursorTaskId = lastEventId.substring(0, separator);
            String seqPart = lastEventId.substring(separator + 1);
            if (!taskId.equals(cursorTaskId)) {
                throw new RuntimeBusinessException(RuntimeExceptionCode.VALIDATION, "事件游标不属于当前目标");
            }
            try {
                return Long.parseLong(seqPart);
            } catch (NumberFormatException exception) {
                throw new RuntimeBusinessException(RuntimeExceptionCode.VALIDATION, "事件游标格式无效");
            }
        }
        if (after != null && !after.isBlank()) {
            try {
                return Long.parseLong(after);
            } catch (NumberFormatException exception) {
                throw new RuntimeBusinessException(RuntimeExceptionCode.VALIDATION, "after 游标无效");
            }
        }
        return 0L;
    }

    /**
     * 查询当前用户的命令回执，不能通过命令 ID 枚举他人目标。
     */
    @GetMapping("/commands/{commandId}")
    public CommandResponse getCommand(HttpServletRequest servletRequest,
                                      @PathVariable String commandId) {
        return goalTaskService.findCommand(UserContext.current(servletRequest).userId(), commandId);
    }

}
