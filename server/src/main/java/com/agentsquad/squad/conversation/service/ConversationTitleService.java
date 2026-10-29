package com.agentsquad.squad.conversation.service;

import com.agentsquad.squad.conversation.dto.ConversationView;
import com.agentsquad.squad.common.exception.RuntimeBusinessException;
import com.agentsquad.squad.common.exception.RuntimeExceptionCode;
import com.agentsquad.squad.conversation.domain.ConversationTitles;
import com.agentsquad.squad.conversation.repository.ConversationMapper;
import com.agentsquad.squad.conversation.repository.MessageAcceptanceMapper;
import com.agentsquad.squad.conversation.dto.ConversationTitleUpdate;
import com.agentsquad.squad.presentation.domain.ConversationHeader;
import com.agentsquad.squad.presentation.dto.ConversationListPage;
import com.agentsquad.squad.presentation.repository.ConversationReadMapper;
import com.agentsquad.squad.common.util.RuntimePayloads;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Base64;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * 会话主题管理与活动顺序分页。
 * <p>
 * 首条消息使用免费确定性标题，不依赖模型成功；手动名称采用独立CAS，列表游标绑定当前用户。
 */
@Service
public class ConversationTitleService {
    @Resource
    private ConversationMapper conversations;

    @Resource
    private ConversationReadMapper reads;

    @Resource
    private MessageAcceptanceMapper events;

    @Resource
    private ObjectMapper json;

    @Transactional
    public ConversationView rename(String userId, String conversationId, ConversationTitleUpdate request) {
        String title = ConversationTitles.safe(request.title(), 200);
        if (title.isBlank() || request.expectedTitleVersion() == null || request.expectedTitleVersion() < 0) {
            throw new RuntimeBusinessException(RuntimeExceptionCode.VALIDATION, "标题不能为空或只含控制字符");
        }
        var row = conversations.lockOwned(userId, conversationId);
        if (row == null || !"ACTIVE".equals(row.status())) {
            throw new RuntimeBusinessException(RuntimeExceptionCode.NOT_FOUND, "对话不存在");
        }
        if (reads.rename(userId, conversationId, title, request.expectedTitleVersion()) != 1) {
            throw new RuntimeBusinessException(RuntimeExceptionCode.CONFLICT, "标题已变化，请刷新后再修改");
        }
        var updated = reads.header(userId, conversationId);
        events.reserveEvent(userId, conversationId);
        events.insertEvent(userId, conversationId, row.generation(), events.eventSequence(userId, conversationId), "title:" + conversationId + ":" + updated.titleVersion(), "conversation.title_updated", RuntimePayloads.toJson(json, Map.of("title", title, "titleVersion", updated.titleVersion())), LocalDateTime.now(ZoneOffset.UTC));
        return ConversationView.from(updated);
    }

    @Transactional(readOnly = true)
    public ConversationListPage list(String userId, String cursor, int limit) {
        if (limit < 1 || limit > 100) {
            throw new RuntimeBusinessException(RuntimeExceptionCode.VALIDATION, "分页大小无效");
        }
        LocalDateTime time = null;
        Long id = null;
        if (cursor != null && !cursor.isBlank()) {
            try {
                String[] parts = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8).split("\\|", -1);
                if (parts.length != 3 || !userId.equals(parts[0])) {
                    throw new IllegalArgumentException();
                }
                time = LocalDateTime.parse(parts[1]);
                id = Long.parseLong(parts[2]);
                if (id < 1) {
                    throw new IllegalArgumentException();
                }
            } catch (IllegalArgumentException | java.time.format.DateTimeParseException exception) {
                throw new RuntimeBusinessException(RuntimeExceptionCode.VALIDATION, "会话列表游标无效");
            }
        }
        var rows = reads.conversations(userId, time, id, limit + 1);
        var page = rows.subList(0, Math.min(rows.size(), limit));
        String next = null;
        if (rows.size() > limit) {
            var last = page.getLast();
            next = Base64.getUrlEncoder().withoutPadding().encodeToString((userId + "|" + last.lastActivityAt() + "|" + reads.conversationId(userId, last.conversationId())).getBytes(StandardCharsets.UTF_8));
        }
        return new ConversationListPage(page.stream().map(ConversationView::from).toList(), next);
    }
}
