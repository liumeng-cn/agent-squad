package com.agentsquad.squad.conversation.domain;

/**
 * 会话主题的纯文本规范。
 * <p>
 * 移除控制与格式字符、折叠空白并按码点限制长度，不读取附件或生成模型请求。
 */
public final class ConversationTitles {
    private ConversationTitles() {
    }

    public static String safe(String text, int limit) {
        if (text == null) {
            return "";
        }
        String title = text.replaceAll("[\\p{Cc}\\p{Cf}]+", " ").replaceAll("(?U)\\s+", " ").strip();
        int count = title.codePointCount(0, title.length());
        return count > limit ? title.substring(0, title.offsetByCodePoints(0, limit)) : title;
    }
}
