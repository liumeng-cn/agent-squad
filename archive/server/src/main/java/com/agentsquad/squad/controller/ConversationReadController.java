package com.agentsquad.squad.controller;

import com.agentsquad.squad.common.model.ApiResult;
import com.agentsquad.squad.identity.UserContext;
import com.agentsquad.squad.model.dto.ConversationTitleUpdateDTO;
import com.agentsquad.squad.model.vo.ConversationSnapshotVO;
import com.agentsquad.squad.model.vo.ConversationViewVO;
import com.agentsquad.squad.service.ConversationEventStreamerService;
import com.agentsquad.squad.service.ConversationQueryService;
import com.agentsquad.squad.service.ConversationTitleService;
import jakarta.annotation.Resource;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
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
    private ConversationQueryService queries;

    @Resource
    private ConversationEventStreamerService events;

    @GetMapping("/snapshot")
    public ApiResult<ConversationSnapshotVO> snapshot(HttpServletRequest servletRequest,
                                                      @PathVariable Long conversationId, @RequestParam(required = false) Long beforeMessageSeq,
                                                      @RequestParam(defaultValue = "40") int limit) {
        return ApiResult.success(queries.snapshot(UserContext.current(servletRequest).userId(), conversationId, beforeMessageSeq, limit));
    }

    @GetMapping(value = "/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(HttpServletRequest servletRequest,
                             @PathVariable Long conversationId, @RequestParam(required = false) String after,
                             @RequestHeader(value = "Last-Event-ID", required = false) String lastEventId) {
        if (servletRequest.getDispatcherType() == DispatcherType.ASYNC) {
            // 连接结束或断开后容器会再派发一次，此时响应已提交，不能再建立订阅。
            return null;
        }
        return events.stream(UserContext.current(servletRequest).userId(), conversationId,
                lastEventId != null && !lastEventId.isBlank() ? lastEventId : after);
    }

    @PatchMapping("/title")
    public ApiResult<ConversationViewVO> rename(
            HttpServletRequest servletRequest, @PathVariable Long conversationId,
            @Valid @RequestBody ConversationTitleUpdateDTO request) {
        return ApiResult.success(titles.rename(UserContext.current(servletRequest).userId(), conversationId, request));
    }
}
