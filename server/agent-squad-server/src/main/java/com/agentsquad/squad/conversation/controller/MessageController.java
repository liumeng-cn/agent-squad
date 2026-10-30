package com.agentsquad.squad.conversation.controller;

import com.agentsquad.squad.conversation.dto.MessageCreateRequest;
import com.agentsquad.squad.conversation.dto.MessageReceipt;
import com.agentsquad.squad.conversation.service.MessageAcceptanceService;
import com.agentsquad.squad.identity.AuthService;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 对话消息提交HTTP边界。
 * <p>
 * 首发可原子创建会话，后续按归属会话受理；用户身份只取登录态，不从请求接受任务或Scope身份。
 */
@RestController
@RequestMapping("/api/v1/conversations")
public class MessageController {

    @Resource
    private AuthService auth;

    @Resource
    private MessageAcceptanceService messages;

    @PostMapping("/messages")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public MessageReceipt first(@CookieValue(value = "SQUAD_SESSION", required = false) String token,
                                @RequestHeader(value = "X-CSRF-Token", required = false) String csrf,
                                @RequestHeader("Idempotency-Key") String key,
                                @Valid @RequestBody MessageCreateRequest request) {
        return messages.accept(auth.authenticateWrite(token, csrf).userId(), null, key, request);
    }

    @PostMapping("/{conversationId}/messages")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public MessageReceipt append(@CookieValue(value = "SQUAD_SESSION", required = false) String token,
                                 @RequestHeader(value = "X-CSRF-Token", required = false) String csrf,
                                 @PathVariable String conversationId,
                                 @RequestHeader("Idempotency-Key") String key,
                                 @Valid @RequestBody MessageCreateRequest request) {
        return messages.accept(auth.authenticateWrite(token, csrf).userId(), conversationId, key, request);
    }
}
