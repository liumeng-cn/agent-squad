package com.agentsquad.squad.po;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * 用户请求的最终答复记录
 *
 * 保存负责发布答复的回复执行或目标任务、发布状态及已发布消息编号。
 * <p>
 * 保存当前代际的所有者和已发布消息，自动恢复不得创建新的交付代际。
 */
@Getter
@Setter
@ToString
@EqualsAndHashCode
@NoArgsConstructor
@AllArgsConstructor
public class FinalDeliveryPO {
    /** 原始用户消息ID */
    private String sourceMessageId;
    /** 交付代际 */
    private long generation;
    /** 负责发布答复的类型[RESPONSE-回复执行,TASK-目标任务] */
    private String ownerType;
    /** 负责发布答复的回复执行ID或目标任务ID */
    private String ownerId;
    /** 答复状态[OWNED-已指定负责人,PUBLISHED-已发布,CLOSED-已关闭] */
    private String state;
    /** 已发布的最终答复消息ID */
    private String publishedMessageId;

    /**
     * 判断交付是否由指定类型和标识持有
     */
    public boolean ownedBy(String type, String id) {
        return ownerType.equals(type) && ownerId.equals(id);
    }
}
