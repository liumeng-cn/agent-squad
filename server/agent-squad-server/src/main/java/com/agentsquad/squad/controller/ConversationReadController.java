package com.agentsquad.squad.controller;

import com.agentsquad.squad.common.model.ApiResult;
import com.agentsquad.squad.domain.request.ConversationTitleUpdateRequest;
import com.agentsquad.squad.domain.response.ConversationViewResponse;
import com.agentsquad.squad.service.ConversationTitleService;
import com.agentsquad.squad.domain.response.ConversationSnapshotResponse;
import com.agentsquad.squad.service.ConversationQueryService;
import com.agentsquad.squad.service.ConversationEventStreamer;
import com.agentsquad.squad.identity.UserContext;
import com.agentsquad.squad.domain.response.ActivityHistoryPageResponse;
import com.agentsquad.squad.domain.request.ActivityHistoryRequest;
import org.springframework.web.bind.annotation.ModelAttribute;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.HttpServletRequest;
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
    private ConversationEventStreamer events;

    @GetMapping("/snapshot")
    public ApiResult<ConversationSnapshotResponse> snapshot(HttpServletRequest servletRequest,
                                                    @PathVariable String conversationId, @RequestParam(required = false) Long beforeMessageSeq,
                                                    @RequestParam(defaultValue = "40") int limit) {
        return ApiResult.success(queries.snapshot(UserContext.current(servletRequest).userId(), conversationId, beforeMessageSeq, limit));
    }

    @GetMapping("/activities")
    public ApiResult<ActivityHistoryPageResponse> activities(HttpServletRequest request, @PathVariable String conversationId,
                                                     @ModelAttribute ActivityHistoryRequest query) {
        return ApiResult.success(queries.activityHistory(UserContext.current(request).userId(), conversationId, query));
    }

    @GetMapping(value = "/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(HttpServletRequest servletRequest,
                             @PathVariable String conversationId, @RequestParam(required = false) String after,
                             @RequestHeader(value = "Last-Event-ID", required = false) String lastEventId) {
        if (servletRequest.getDispatcherType() == DispatcherType.ASYNC) {
            // 连接结束或断开后容器会再派发一次，此时响应已提交，不能再建立订阅。
            return null;
        }
        return events.stream(UserContext.current(servletRequest).userId(), conversationId,
                lastEventId != null && !lastEventId.isBlank() ? lastEventId : after);
    }

    @PatchMapping("/title")
    public ApiResult<ConversationViewResponse> rename(
            HttpServletRequest servletRequest, @PathVariable String conversationId,
            @Valid @RequestBody ConversationTitleUpdateRequest request) {
        return ApiResult.success(titles.rename(UserContext.current(servletRequest).userId(), conversationId, request));
    }
}
