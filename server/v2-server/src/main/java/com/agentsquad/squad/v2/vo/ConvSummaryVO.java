package com.agentsquad.squad.v2.vo;

import com.agentsquad.squad.v2.enums.ConvTitleSource;
import lombok.Data;

import java.time.Instant;

/**
 * 对话目录摘要
 * <p>
 * 列表、详情、改名和会话快照共用的公开结构，只包含目录字段，不包含消息和执行细节。
 */
@Data
public class ConvSummaryVO {
    /**
     * 对话ID
     */
    private String convId;

    /**
     * 关联的原生会话ID，用于读取和订阅消息
     */
    private String sessionId;

    /**
     * 对话标题
     */
    private String title;

    /**
     * 标题来源
     */
    private ConvTitleSource titleSource;

    /**
     * 标题版本，改名按该版本条件更新
     */
    private long version;

    /**
     * 建档时间
     */
    private Instant createdAt;

    /**
     * 对话备注
     */
    private String remark;

    /**
     * 是否已归档
     */
    private boolean archived;
}
