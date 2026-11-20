package com.agentsquad.squad.v2.controller;

import com.agentsquad.squad.common.model.ApiResult;
import com.agentsquad.squad.common.utils.CurrentUserContext;
import com.agentsquad.squad.v2.dto.ChatInterruptDTO;
import com.agentsquad.squad.v2.dto.ChatMessageDTO;
import com.agentsquad.squad.v2.dto.ChatResumeDTO;
import com.agentsquad.squad.v2.service.ChatEventStreamService;
import com.agentsquad.squad.v2.service.ChatRuntimeService;
import com.agentsquad.squad.v2.service.ChatService;
import com.agentsquad.squad.v2.vo.ChatReceiptVO;
import com.agentsquad.squad.v2.vo.ChatSessionVO;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

/**
 * 聊天接口
 * <p>
 * 发送和订阅连接原生执行链路，历史列表适配独立维护。
 */
@RestController
public class ChatController {
    @Resource
    private ChatService chat;

    @Resource
    private ChatRuntimeService sessions;

    @Resource
    private ChatEventStreamService events;

    /**
     * 发送消息
     */
    @PostMapping("/api/v2/chat/messages")
    public ApiResult<ChatReceiptVO> send(@Valid @RequestBody ChatMessageDTO request) {
        return ApiResult.success(chat.send(request, CurrentUserContext.getUserId()));
    }

    /**
     * 获取会话消息
     */
    @GetMapping("/api/v2/chat/sessions/{sessionId}")
    public ApiResult<ChatSessionVO> session(@PathVariable String sessionId) {
        return ApiResult.success(sessions.read(sessionId, CurrentUserContext.getUserId()));
    }

    /**
     * 订阅会话消息
     */
    @GetMapping(value = "/api/v2/chat/sessions/{sessionId}/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<ChatSessionVO>> events(@PathVariable String sessionId) {
        return events.subscribe(sessionId, CurrentUserContext.getUserId());
    }

    /**
     * 停止会话执行
     */
    @PostMapping("/api/v2/chat/interruptions")
    public ApiResult<Boolean> interrupt(@Valid @RequestBody ChatInterruptDTO request) {
        return ApiResult.success(chat.interrupt(request, CurrentUserContext.getUserId()));
    }

    /**
     * 继续会话执行
     */
    @PostMapping("/api/v2/chat/resumptions")
    public ApiResult<String> resume(@Valid @RequestBody ChatResumeDTO request) {
        return ApiResult.success(chat.resume(request, CurrentUserContext.getUserId()));
    }
}
