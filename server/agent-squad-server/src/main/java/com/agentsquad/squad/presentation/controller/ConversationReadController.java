package com.agentsquad.squad.presentation.controller;

import com.agentsquad.squad.conversation.dto.ConversationTitleUpdate;
import com.agentsquad.squad.conversation.dto.ConversationView;
import com.agentsquad.squad.conversation.service.ConversationTitleService;
import com.agentsquad.squad.conversation.dto.ConversationSnapshot;
import com.agentsquad.squad.presentation.service.ConversationQueryService;
import com.agentsquad.squad.presentation.service.ConversationEventStreamer;
import com.agentsquad.squad.identity.AuthService;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 对话快照与公开订阅的HTTP入口。
 * <p>
 * 用户身份只取同源登录态，翻页与重连均不创建命令或调用模型。
 */
@RestController
@RequestMapping("/api/v1/conversations/{conversationId}")
public class ConversationReadController {
    @Resource
    private ConversationTitleService titles;

    @Resource
    private AuthService auth;

    @Resource
    private ConversationQueryService queries;

    @Resource
    private ConversationEventStreamer events;

    @GetMapping("/snapshot")
    public ConversationSnapshot snapshot(@CookieValue(value = "SQUAD_SESSION", required = false) String token,
                                         @PathVariable String conversationId, @RequestParam(required = false) Long beforeMessageSeq,
                                         @RequestParam(defaultValue = "40") int limit) {
        return queries.snapshot(auth.authenticate(token).userId(), conversationId, beforeMessageSeq, limit);
    }

    @GetMapping(value = "/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@CookieValue(value = "SQUAD_SESSION", required = false) String token,
                             @PathVariable String conversationId, @RequestParam(required = false) String after,
                             @RequestHeader(value = "Last-Event-ID", required = false) String lastEventId) {
        return events.stream(auth.authenticate(token).userId(), conversationId,
                lastEventId != null && !lastEventId.isBlank() ? lastEventId : after);
    }

    @PatchMapping("/title")
    public ConversationView rename(
            @CookieValue(value = "SQUAD_SESSION", required = false) String token,
            @RequestHeader(value = "X-CSRF-Token", required = false) String csrf, @PathVariable String conversationId,
            @Valid @RequestBody ConversationTitleUpdate request) {
        return titles.rename(auth.authenticateWrite(token, csrf).userId(), conversationId, request);
    }
}
