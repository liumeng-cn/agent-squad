package com.agentsquad.squad.nativechat;

import com.agentsquad.squad.identity.UserContext;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/** 原生事件聊天入口：一次 POST 直接返回 SSE，不经过旧版 Conversation/Task 业务链路。 */
@RestController
@RequestMapping("/api/v1/native-chat")
public class NativeChatController {
    @Resource
    private NativeChatService nativeChatService;

    @Resource
    private NativeSubagentService nativeSubagentService;

    @PostMapping(path = "/message", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter message(HttpServletRequest request, @Valid @RequestBody NativeChatRequest body) {
        UserContext.current(request);
        return nativeChatService.stream(body);
    }
    @PostMapping(path = "/subagents", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter subagents(HttpServletRequest request, @Valid @RequestBody NativeSubagentController.SubagentRequest body) {
        UserContext.current(request);
        return nativeSubagentService.stream(body.task());
    }
}
