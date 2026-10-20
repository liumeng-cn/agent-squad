package com.agentsquad.squad.runtime.controller;

import com.agentsquad.squad.identity.AuthService;
import com.agentsquad.squad.runtime.dto.*;
import com.agentsquad.squad.runtime.service.GoalTaskService;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

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
     * 按事件序号回放当前用户的公开事件。
     */
    @GetMapping("/tasks/{taskId}/events")
    public List<TaskEventResponse> getEvents(
            @CookieValue(value = "SQUAD_SESSION", required = false) String token,
            @PathVariable String taskId,
            @RequestParam(defaultValue = "0") long after) {
        return goalTaskService.listEvents(userId(token), taskId, after);
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
