package com.agentsquad.squad.interaction.controller;

import com.agentsquad.squad.identity.AuthService;
import com.agentsquad.squad.conversation.dto.CommandReceipt;
import com.agentsquad.squad.presentation.dto.Interaction;
import com.agentsquad.squad.presentation.dto.InteractionPage;
import com.agentsquad.squad.interaction.dto.InteractionAnswerRequest;
import com.agentsquad.squad.interaction.service.InteractionQueryService;
import com.agentsquad.squad.interaction.service.InteractionService;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

/**
 * 持久交互 HTTP 边界。用户归属只取认证会话；答复受理返回命令回执，不代表模型已收到。
 * 交互仅在投递启用时存在，接口随投递开关启用。
 */
@RestController
@RequestMapping("/api/v1")
@ConditionalOnProperty(prefix = "agentsquad.delivery", name = "enabled", havingValue = "true")
public class InteractionController {

    @Resource
    private AuthService authService;

    @Resource
    private InteractionQueryService interactionQueryService;

    @Resource
    private InteractionService interactionService;

    /**
     * 列出当前用户目标的交互；审批卡与追问卡按 status 区分待办与历史。
     */
    @GetMapping("/tasks/{taskId}/interactions")
    public InteractionPage listByTask(
            @CookieValue(value = "SQUAD_SESSION", required = false) String token,
            @PathVariable String taskId,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "20") int limit) {
        return interactionQueryService.listPage(authService.authenticate(token).userId(),
                taskId, cursor, limit);
    }

    /**
     * 读取当前用户单个交互；用于答复前的核对与刷新。
     */
    @GetMapping("/interactions/{interactionId}")
    public Interaction getOwned(
            @CookieValue(value = "SQUAD_SESSION", required = false) String token,
            @PathVariable String interactionId) {
        return interactionQueryService.getOwned(authService.authenticate(token).userId(),
                interactionId);
    }

    /**
     * 受理交互答复；202 仅表示答复已持久受理并入队，模型回填以命令投递结果为准。
     */
    @PostMapping("/interactions/{interactionId}/responses")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public CommandReceipt respond(
            @CookieValue(value = "SQUAD_SESSION", required = false) String token,
            @RequestHeader(value = "X-CSRF-Token", required = false) String csrfToken,
            @PathVariable String interactionId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody InteractionAnswerRequest request) {
        return interactionService.respond(authService.authenticateWrite(token, csrfToken),
                interactionId, idempotencyKey, request.expectedVersion(), request.kind(),
                request.approve(), request.confirmedRequestHash(), request.value());
    }
}
