package com.agentsquad.squad.v2.controller;

import com.agentsquad.squad.v2.dto.ChatMessageDTO;
import com.agentsquad.squad.v2.service.ChatService;
import io.agentscope.core.agui.event.AguiEvent;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;

/**
 * 聊天 HTTP 入口
 * <p>
 * 校验协议与网关身份，将服务事件流作为 SSE 输出，不在控制器管理 Agent 执行。
 */
@RestController
public class ChatController {
    @Resource
    private ChatService chatService;

    @Resource
    private Environment environment;

    /**
     * 单次提交直接返回本轮事件流；本地模式使用个人测试身份。
     */
    @PostMapping(value = "/api/v2/chat/messages", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<AguiEvent>> send(@Valid @RequestBody ChatMessageDTO request,
                                                   HttpServletRequest httpRequest) {
        String userId = httpRequest.getHeader("X-User-Id");
        if (userId == null && environment.acceptsProfiles(Profiles.of("local"))) {
            userId = "1";
        }
        if (userId == null || !userId.matches("[1-9][0-9]{0,18}")) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "缺少有效用户身份");
        }
        return chatService.sendAgui(request, userId)
                .map(event -> ServerSentEvent.<AguiEvent>builder(event).event("chat").build());
    }
}
