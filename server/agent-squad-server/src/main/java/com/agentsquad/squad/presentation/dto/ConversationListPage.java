package com.agentsquad.squad.presentation.dto;

import com.agentsquad.squad.conversation.dto.ConversationView;

import java.util.List;

/**
 * 按真实活动时间排序的会话页。
 * <p>
 * 复合游标固定上一页末项的排序值，标题及日期均来自服务端事实。
 */
public record ConversationListPage(List<ConversationView> items, String nextCursor) {
}
