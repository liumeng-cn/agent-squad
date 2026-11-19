package com.agentsquad.squad.v2.controller;

import com.agentsquad.squad.common.model.ApiResult;
import com.agentsquad.squad.common.utils.CurrentUserContext;
import com.agentsquad.squad.v2.dto.SessionCreateDTO;
import com.agentsquad.squad.v2.dto.SessionRenameDTO;
import com.agentsquad.squad.v2.service.ChatSessionService;
import com.agentsquad.squad.v2.vo.SessionPageVO;
import com.agentsquad.squad.v2.vo.SessionSummaryVO;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 会话接口
 * <p>
 * 暂时使用mock响应完成页面，正式存储方案待确认。
 */
@RestController
@RequestMapping("/api/v2/chat/sessions")
public class ChatSessionController {
    @Resource
    private ChatSessionService sessions;

    /**
     * 创建会话
     */
    @PostMapping
    public ApiResult<SessionSummaryVO> create(@Valid @RequestBody SessionCreateDTO request) {
        return ApiResult.success(sessions.create(request.getSessionId(), CurrentUserContext.getUserId()));
    }

    /**
     * 获取会话列表
     */
    @GetMapping
    public ApiResult<SessionPageVO> list(@RequestParam(defaultValue = "30") int limit, @RequestParam(required = false) String cursor) {
        return ApiResult.success(sessions.list(CurrentUserContext.getUserId(), limit, cursor));
    }

    /**
     * 修改会话名称
     */
    @PatchMapping("/{sessionId}")
    public ApiResult<SessionSummaryVO> rename(@PathVariable String sessionId, @Valid @RequestBody SessionRenameDTO request) {
        return ApiResult.success(sessions.rename(sessionId, CurrentUserContext.getUserId(), request));
    }
}
