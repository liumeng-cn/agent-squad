package com.agentsquad.squad.v2.controller;

import com.agentsquad.squad.common.model.ApiResult;
import com.agentsquad.squad.v2.dto.ChatInterruptDTO;
import com.agentsquad.squad.v2.dto.ChatMessageDTO;
import com.agentsquad.squad.v2.dto.ChatResumeDTO;
import com.agentsquad.squad.v2.service.ChatEventStreamService;
import com.agentsquad.squad.v2.service.ChatService;
import com.agentsquad.squad.v2.service.ChatSessionService;
import com.agentsquad.squad.v2.vo.ChatReceiptVO;
import com.agentsquad.squad.v2.vo.ChatSessionVO;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;

/**
 * 聊天 HTTP 入口
 * <p>
 * 提交和订阅分离；身份取自网关，控制器只处理协议、参数和服务调用。
 */
@Validated
@RestController
public class ChatController {
    @Resource
    private ChatService chatService;

    @Resource
    private ChatSessionService sessionService;

    @Resource
    private ChatEventStreamService streamService;

    @Resource
    private Environment environment;

    /**
     * 接收消息并返回原生收据，不等待模型完成。
     */
    @PostMapping("/api/v2/chat/messages")
    public ApiResult<ChatReceiptVO> send(@Valid @RequestBody ChatMessageDTO request, HttpServletRequest http) {
        return ApiResult.success(chatService.send(request, userId(http)));
    }

    /**
     * 返回公开历史和执行状态，读取不触发执行。
     */
    @GetMapping("/api/v2/chat/sessions/{sessionId}")
    public ApiResult<ChatSessionVO> session(
            @PathVariable @Pattern(regexp = "[a-zA-Z0-9-]{1,64}") String sessionId, HttpServletRequest http) {
        return ApiResult.success(sessionService.read(sessionId, userId(http)));
    }

    /**
     * 订阅公开会话快照；连接取消只结束观察。
     */
    @GetMapping(value = "/api/v2/chat/sessions/{sessionId}/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<ChatSessionVO>> events(
            @PathVariable @Pattern(regexp = "[a-zA-Z0-9-]{1,64}") String sessionId, HttpServletRequest http) {
        return streamService.subscribe(sessionId, userId(http));
    }

    /**
     * 停止指定执行，不将收据伪装成最终停止状态。
     */
    @PostMapping("/api/v2/chat/interruptions")
    public ApiResult<Boolean> interrupt(@Valid @RequestBody ChatInterruptDTO request, HttpServletRequest http) {
        return ApiResult.success(chatService.interrupt(request, userId(http)));
    }

    /**
     * 显式继续未完成轮次，保留原生恢复准入约束。
     */
    @PostMapping("/api/v2/chat/resumptions")
    public ApiResult<String> resume(@Valid @RequestBody ChatResumeDTO request, HttpServletRequest http) {
        return ApiResult.success(chatService.resume(request, userId(http)));
    }

    private String userId(HttpServletRequest request) {
        String userId = request.getHeader("X-User-Id");
        if (userId == null && environment.acceptsProfiles(Profiles.of("local"))) {
            userId = "1";
        }
        if (userId == null || !userId.matches("[1-9][0-9]{0,18}")) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "缺少有效用户身份");
        }
        return userId;
    }
}
