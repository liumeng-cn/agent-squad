package com.agentsquad.squad.v2.service;

import com.agentsquad.squad.v2.dto.SessionRenameDTO;
import com.agentsquad.squad.v2.enums.SessionTitleSource;
import com.agentsquad.squad.v2.vo.ChatSessionVO;
import com.agentsquad.squad.v2.vo.SessionPageVO;
import com.agentsquad.squad.v2.vo.SessionSummaryVO;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.Resource;
import java.io.IOException;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

/**
 * 会话页面数据
 *
 * 暂时读取固定JSON展示页面，只回显写请求，不维护模拟状态或执行真实任务。
 */
@Service
public class ChatSessionService {
    @Resource
    private ObjectMapper mapper;

    public SessionPageVO list(String userId, int limit, String cursor) {
        return readJson("sessions", SessionPageVO.class);
    }

    public SessionSummaryVO create(String sessionId, String userId) {
        var result = readJson("session-summary", SessionSummaryVO.class);
        result.setSessionId(sessionId);
        return result;
    }

    public ChatSessionVO read(String sessionId, String userId) {
        String fixture = switch (sessionId) {
            case "demo-welcome", "demo-api", "demo-plan" -> sessionId;
            default -> "session";
        };
        var result = readJson(fixture, ChatSessionVO.class);
        result.setSessionId(sessionId);
        if (result.getSummary() != null) {
            result.getSummary().setSessionId(sessionId);
        }
        return result;
    }

    public SessionSummaryVO rename(String sessionId, String userId, SessionRenameDTO request) {
        var result = create(sessionId, userId);
        result.setTitle(request.getTitle());
        result.setTitleSource(SessionTitleSource.MANUAL);
        result.setVersion(request.getVersion() + 1);
        return result;
    }

    private <T> T readJson(String name, Class<T> type) {
        try (var input = new ClassPathResource("preview/" + name + ".json").getInputStream()) {
            return mapper.readValue(input, type);
        } catch (IOException exception) {
            throw new IllegalStateException("页面JSON数据加载失败", exception);
        }
    }
}
