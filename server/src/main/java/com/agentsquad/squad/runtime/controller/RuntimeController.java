package com.agentsquad.squad.runtime.controller;

import com.agentsquad.squad.identity.AuthService;
import com.agentsquad.squad.runtime.dto.*;
import com.agentsquad.squad.runtime.service.ConversationDeletionService;
import com.agentsquad.squad.runtime.service.GoalTaskService;
import com.agentsquad.squad.runtime.service.RuntimeBusinessException;
import com.agentsquad.squad.runtime.service.RuntimeExceptionCode;
import com.agentsquad.squad.runtime.service.TaskControlService;
import com.agentsquad.squad.runtime.service.TaskEventStreamer;
import com.agentsquad.squad.runtime.service.TaskSnapshotService;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

/**
 * 目标受理和查询 HTTP 边界。用户归属只取认证会话，不接受可伪造的身份请求头。
 *
 * <p>Controller 负责参数和协议；事务、幂等、归属与持久事件由 GoalTaskService 负责。</p>
 */
@RestController
@RequestMapping("/api/v1")
public class RuntimeController {

    @Resource
    private AuthService authService;

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
     * 创建当前用户的对话。
     */
    @PostMapping("/conversations")
    @ResponseStatus(HttpStatus.CREATED)
    public ConversationResponse createConversation(
            @CookieValue(value = "SQUAD_SESSION", required = false) String token,
            @RequestHeader(value = "X-CSRF-Token", required = false) String csrfToken,
            @Valid @RequestBody ConversationCreateRequest request) {
        return goalTaskService.createConversation(authService.authenticateWrite(token, csrfToken), request.title());
    }

    /**
     * 列出当前用户的会话，cursor 为存储主键 id。
     */
    @GetMapping("/conversations")
    public ConversationPage listConversations(
            @CookieValue(value = "SQUAD_SESSION", required = false) String token,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "20") int limit) {
        return goalTaskService.listConversations(userId(token), cursor, limit);
    }

    /**
     * 读取当前用户单个会话快照。
     */
    @GetMapping("/conversations/{conversationId}")
    public ConversationResponse getConversation(
            @CookieValue(value = "SQUAD_SESSION", required = false) String token,
            @PathVariable String conversationId) {
        return goalTaskService.getConversation(userId(token), conversationId);
    }

    /**
     * 受理删除当前用户会话；202 仅表示删除已持久受理，清理异步完成。
     */
    @DeleteMapping("/conversations/{conversationId}")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void deleteConversation(
            @CookieValue(value = "SQUAD_SESSION", required = false) String token,
            @RequestHeader(value = "X-CSRF-Token", required = false) String csrfToken,
            @PathVariable String conversationId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestHeader("If-Match") String ifMatch) {
        conversationDeletionService.delete(authService.authenticateWrite(token, csrfToken),
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
            @CookieValue(value = "SQUAD_SESSION", required = false) String token,
            @PathVariable String conversationId,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "20") int limit) {
        return goalTaskService.listConversationTasks(userId(token), conversationId, cursor, limit);
    }

    /**
     * 按 Idempotency-Key 受理目标，202 仅表示平台事务已提交。
     */
    @PostMapping("/conversations/{conversationId}/tasks")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public TaskReceipt createTask(
            @CookieValue(value = "SQUAD_SESSION", required = false) String token,
            @RequestHeader(value = "X-CSRF-Token", required = false) String csrfToken,
            @PathVariable String conversationId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody TaskCreateRequest request) {
        return goalTaskService.submit(authService.authenticateWrite(token, csrfToken),
                conversationId, idempotencyKey, request.message());
    }

    /**
     * 查询当前用户的目标状态。
     */
    @GetMapping("/tasks/{taskId}")
    public TaskResponse getTask(@CookieValue(value = "SQUAD_SESSION", required = false) String token,
                                @PathVariable String taskId) {
        return goalTaskService.findTask(userId(token), taskId);
    }

    /**
     * 查询当前用户的目标详情快照。
     */
    @GetMapping("/tasks/{taskId}/snapshot")
    public TaskSnapshot getTaskSnapshot(@CookieValue(value = "SQUAD_SESSION", required = false) String token,
                                        @PathVariable String taskId) {
        return taskSnapshotService.snapshot(userId(token), taskId);
    }

    /**
     * 取消当前用户的未结束目标。
     */
    @PostMapping("/tasks/{taskId}/cancel")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public CommandReceipt cancelTask(
            @CookieValue(value = "SQUAD_SESSION", required = false) String token,
            @RequestHeader(value = "X-CSRF-Token", required = false) String csrfToken,
            @PathVariable String taskId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody ControlRequest request) {
        return taskControlService.cancel(authService.authenticateWrite(token, csrfToken),
                taskId, idempotencyKey, request.expectedVersion());
    }

    /**
     * 恢复当前用户的可恢复目标。
     */
    @PostMapping("/tasks/{taskId}/resume")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public CommandReceipt resumeTask(
            @CookieValue(value = "SQUAD_SESSION", required = false) String token,
            @RequestHeader(value = "X-CSRF-Token", required = false) String csrfToken,
            @PathVariable String taskId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody ControlRequest request) {
        return taskControlService.resume(authService.authenticateWrite(token, csrfToken),
                taskId, idempotencyKey, request.expectedVersion());
    }

    /**
     * 以 SSE 方式订阅当前用户的任务公开事件；首次可传 after，重连以 Last-Event-ID 为准。
     */
    @GetMapping(value = "/tasks/{taskId}/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter observeEvents(
            @CookieValue(value = "SQUAD_SESSION", required = false) String token,
            @PathVariable String taskId,
            @RequestHeader(value = "Last-Event-ID", required = false) String lastEventId,
            @RequestParam(required = false) String after) {
        return taskEventStreamer.stream(userId(token), taskId, resolveEventCursor(taskId, lastEventId, after));
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
    public CommandResponse getCommand(@CookieValue(value = "SQUAD_SESSION", required = false) String token,
                                      @PathVariable String commandId) {
        return goalTaskService.findCommand(userId(token), commandId);
    }

    private String userId(String token) {
        return authService.authenticate(token).userId();
    }
}
