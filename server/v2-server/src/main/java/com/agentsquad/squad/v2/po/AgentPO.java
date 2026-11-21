package com.agentsquad.squad.v2.po;

import com.agentsquad.squad.v2.enums.AgentType;
import com.agentsquad.squad.v2.enums.AgentConfigStatus;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.Instant;

/**
 * Agent目录
 * <p>
 * 映射管理模块的 sq_agent 表。
 */
@Data
@TableName(value = "sq_agent", schema = "squad_chat")
public class AgentPO {
    /**
     * 内部主键
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * Agent业务ID
     */
    private Long agentId;

    /**
     * 能力编码
     */
    private String agentCode;

    /**
     * 展示名称
     */
    private String agentName;

    /**
     * 主子类型
     */
    private AgentType agentType;

    /**
     * 启停状态
     */
    private AgentConfigStatus status;

    /**
     * 当前版本ID
     */
    private Long currentVersionId;

    /**
     * 编辑修订号
     */
    private Long revision;

    /**
     * 创建人
     */
    private Long creatorId;

    /**
     * 更新人
     */
    private Long lastUpdaterId;

    /**
     * 更新时间
     */
    private Instant lastUpdateTime;

}
