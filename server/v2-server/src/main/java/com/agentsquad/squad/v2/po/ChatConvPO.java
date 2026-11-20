package com.agentsquad.squad.v2.po;

import com.agentsquad.squad.v2.enums.SessionTitleSource;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.Instant;

/**
 * 聊天会话目录
 * <p>
 * 保存用户归属、固定 Agent 和标题，原生消息由 SDK 持久化。
 */
@TableName(value = "sq_chat_conv", schema = "squad_chat")
@Data
public class ChatConvPO {
    /**
     * 内部存储主键
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 业务对话ID
     */
    private String convId;

    /**
     * 原生会话ID
     */
    private String sessionId;

    /**
     * 归属用户业务ID
     */
    private Long userId;

    /**
     * 固定Agent标识
     */
    private String agentId;

    /**
     * 展示标题
     */
    private String title;

    /**
     * 自动或手动标题
     */
    private SessionTitleSource titleSource;

    /**
     * 标题编辑版本
     */
    private Long version;

    /**
     * 对话备注
     */
    private String remark;

    /**
     * 是否归档
     */
    private Boolean archived;

    /**
     * 是否删除
     */
    private Boolean deleted;

    /**
     * 创建人ID
     */
    private Long creatorId;

    /**
     * 创建人姓名
     */
    private String creatorName;

    /**
     * 建档时间
     */
    private Instant createTime;

    /**
     * 最后更新人ID
     */
    private Long lastUpdaterId;

    /**
     * 最后更新人姓名
     */
    private String lastUpdaterName;

    /**
     * 最后更新时间
     */
    private Instant lastUpdateTime;
}
