package com.agentsquad.squad.v2.controller;

import com.agentsquad.squad.common.model.ApiResult;
import com.agentsquad.squad.v2.dto.ChatInterruptDTO;
import com.agentsquad.squad.v2.dto.ChatAnswerDTO;
import com.agentsquad.squad.v2.service.ChatInteractionService;
import com.agentsquad.squad.v2.dto.ChatMessageDTO;
import com.agentsquad.squad.v2.dto.ChatResumeDTO;
import com.agentsquad.squad.v2.service.AgentRuntimeRegistry;
import com.agentsquad.squad.v2.service.ChatEventStreamService;
import com.agentsquad.squad.v2.service.ChatRuntimeService;
import com.agentsquad.squad.v2.service.ChatService;
import com.agentsquad.squad.v2.vo.ChatAgentOptionVO;
import com.agentsquad.squad.v2.vo.ChatReceiptVO;
import com.agentsquad.squad.v2.vo.ChatSessionVO;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

import java.util.List;

/**
 * 聊天接口
 * <p>
 * 发送、停止、继续和订阅按业务对话标识定位，先核对目录归属再进入原生执行链路。
 */
@RestController
@RequestMapping("/api/v2/chat")
public class ChatController {
    @Resource
    private ChatService chatService;

    @Resource
    private ChatInteractionService interactions;

    @Resource
    private ChatRuntimeService sessionService;

    @Resource
    private ChatEventStreamService eventService;

    @Resource
    private AgentRuntimeRegistry runtimeRegistry;

    /**
     * 列出可供新对话选择的已发布 Master 智能体
     */
    @GetMapping("/agents")
    public ApiResult<List<ChatAgentOptionVO>> listAgents() {
        return ApiResult.success(runtimeRegistry.listAvailableOptions());
    }

    /**
     * 发送消息
     */
    @PostMapping("/messages")
    public ApiResult<ChatReceiptVO> send(@Valid @RequestBody ChatMessageDTO request) {
        return ApiResult.success(chatService.send(request));
    }

    /**
     * 获取会话消息
     */
    @GetMapping("/convs/{convId}/session")
    public ApiResult<ChatSessionVO> session(@PathVariable String convId) {
        return ApiResult.success(sessionService.read(convId));
    }

    /**
     * 订阅会话消息
     */
    @GetMapping(value = "/convs/{convId}/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<ChatSessionVO>> events(@PathVariable String convId) {
        return eventService.subscribe(convId);
    }

    /**
     * 停止会话执行
     */
    @PostMapping("/interruptions")
    public ApiResult<Boolean> interrupt(@Valid @RequestBody ChatInterruptDTO request) {
        return ApiResult.success(chatService.interrupt(request));
    }

    /**
     * 继续会话执行
     */
    @PostMapping("/resumptions")
    public ApiResult<String> resume(@Valid @RequestBody ChatResumeDTO request) {
        return ApiResult.success(chatService.resume(request));
    }

    /**
     * 回答待答问题
     */
    @PostMapping("/answers")
    public ApiResult<String> answer(@Valid @RequestBody ChatAnswerDTO request) {
        return ApiResult.success(interactions.answer(request));
    }
}
