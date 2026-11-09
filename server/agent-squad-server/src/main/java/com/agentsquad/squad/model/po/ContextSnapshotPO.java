package com.agentsquad.squad.model.po;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * Worker 局部材料的不可变数据库记录
 *
 * 快照固定目标节点、对话代际和内容摘要；发布计划与投递 Worker 时均须重新核对归属。
 */
@Getter
@Setter
@ToString
@EqualsAndHashCode
@NoArgsConstructor
@AllArgsConstructor
@TableName("sq_context_snapshot")
public class ContextSnapshotPO {
    /** 局部上下文快照业务ID */
    private String snapshotId;
    /** 资源归属用户ID */
    private String userId;
    /** 所属目标ID */
    private String taskId;
    /** 目标计划节点键 */
    private String targetNodeKey;
    /** 快照幂等逻辑键 */
    private String logicalKey;
    /** 上下文固定时的对话版本号 */
    private long conversationGeneration;
    /** 上下文固定时的记忆授权版本号；未使用长期记忆时为0 */
    private long memoryRevision;
    /** 经平台验证的局部材料及来源 */
    private String itemsJson;
    /** 局部材料令牌估算 */
    private long tokenEstimate;
    /** 局部材料规范化SHA256摘要 */
    private String contentHash;
    /** 准备局部材料时的目标修订号 */
    private long goalRevision;
}
