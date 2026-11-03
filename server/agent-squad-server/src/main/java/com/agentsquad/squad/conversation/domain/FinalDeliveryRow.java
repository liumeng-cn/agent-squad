package com.agentsquad.squad.conversation.domain;

/**
 * 用户请求的最终交付权事实。
 * <p>
 * 保存当前代际的所有者和已发布消息，自动恢复不得创建新的交付代际。
 */
public record FinalDeliveryRow(String sourceMessageId, long generation, String ownerType,
                               String ownerId, String state, String publishedMessageId) {
    public boolean ownedBy(String type, String id) {
        return ownerType.equals(type) && ownerId.equals(id);
    }
}
