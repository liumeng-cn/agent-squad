package com.agentsquad.squad.v2.service;

import com.agentsquad.squad.v2.bo.SquadExecutionBO;
import com.agentsquad.squad.v2.dto.SquadMessageRequestDTO;
import com.agentsquad.squad.v2.event.SquadSseSubscriber;
import com.agentsquad.squad.v2.utils.SquadIdUtils;
import jakarta.annotation.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * V2 消息服务；一个 POST 同时支持新执行和断线补收。
 */
@Service
public class SquadMessageService {
    @Resource
    private SquadExecutionManager executionManager;
    @Resource
    private SquadSseSubscriber sseSubscriber;

    /**
     * 创建执行或恢复已有事件流。
     */
    public SseEmitter stream(SquadMessageRequestDTO request, Long userId) {
        SseEmitter emitter = new SseEmitter(0L);
        SquadExecutionBO execution;
        long afterSequence;
        if (StringUtils.hasText(request.getExecutionId())) {
            if (StringUtils.hasText(request.getMessage()) || StringUtils.hasText(request.getConversationId())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "恢复事件流时只允许传 executionId 和 afterSequence");
            }
            execution = executionManager.require(parseId(request.getExecutionId(), "executionId"), userId);
            afterSequence = request.getAfterSequence() == null ? 0L : request.getAfterSequence();
        } else {
            if (!StringUtils.hasText(request.getMessage())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "message 不能为空");
            }
            if (request.getAfterSequence() != null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "创建新执行时不能传 afterSequence");
            }
            execution = executionManager.submit(userId,
                    parseOptionalId(request.getConversationId(), "conversationId"), request.getMessage().trim());
            afterSequence = 0L;
        }
        sseSubscriber.subscribe(execution, emitter, afterSequence);
        return emitter;
    }

    private Long parseOptionalId(String value, String name) {
        return StringUtils.hasText(value) ? parseId(value, name) : null;
    }

    private Long parseId(String value, String name) {
        try {
            return SquadIdUtils.parsePositiveLong(value);
        } catch (RuntimeException invalid) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, name + " 必须是正整数");
        }
    }
}