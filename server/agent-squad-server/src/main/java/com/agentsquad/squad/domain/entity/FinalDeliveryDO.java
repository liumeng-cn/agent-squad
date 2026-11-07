package com.agentsquad.squad.domain.entity;

/**
 * 用户请求的最终答复记录
 *
 * 保存负责发布答复的回复执行或目标任务、发布状态及已发布消息编号。
 * <p>
 * 保存当前代际的所有者和已发布消息，自动恢复不得创建新的交付代际。
 */
public record FinalDeliveryDO(String sourceMessageId, long generation, String ownerType,
                               String ownerId, String state, String publishedMessageId) {
    public boolean ownedBy(String type, String id) {
        return ownerType.equals(type) && ownerId.equals(id);
    }
}
