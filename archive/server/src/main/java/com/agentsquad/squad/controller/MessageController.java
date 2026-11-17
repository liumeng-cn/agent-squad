package com.agentsquad.squad.controller;

import com.agentsquad.squad.common.model.ApiResult;
import com.agentsquad.squad.identity.UserContext;
import com.agentsquad.squad.model.dto.MessageCreateDTO;
import com.agentsquad.squad.model.vo.MessageReceiptVO;
import com.agentsquad.squad.service.MessageAcceptanceService;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

/**
 * 对话消息提交接口
 * <p>
 * 首发可原子创建会话，后续按归属会话受理；用户身份只取登录态，不从请求接受任务或Scope身份。
 */
@RestController
@RequestMapping("/api/v1/conversations")
public class MessageController {

    @Resource
    private MessageAcceptanceService messages;

    @PostMapping("/messages")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public ApiResult<MessageReceiptVO> first(HttpServletRequest servletRequest,
                                             @RequestHeader("Idempotency-Key") String key,
                                             @Valid @RequestBody MessageCreateDTO request) {
        return ApiResult.success(messages.accept(UserContext.current(servletRequest).userId(), null, key, request));
    }

    @PostMapping("/{conversationId}/messages")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public ApiResult<MessageReceiptVO> append(HttpServletRequest servletRequest,
                                              @PathVariable Long conversationId,
                                              @RequestHeader("Idempotency-Key") String key,
                                              @Valid @RequestBody MessageCreateDTO request) {
        return ApiResult.success(messages.accept(UserContext.current(servletRequest).userId(), conversationId, key, request));
    }
}
