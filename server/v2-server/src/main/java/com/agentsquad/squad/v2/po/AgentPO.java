package com.agentsquad.squad.v2.po;

import com.agentsquad.squad.v2.enums.AgentType;
import com.agentsquad.squad.v2.enums.ConfigReadiness;
import com.agentsquad.squad.v2.enums.ConfigStatus;
import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.Instant;

/**
 * Agent 目录
 * <p>
 * 映射 sq_agent，维护稳定身份、展示别称、最新配置版本指针及 Master 唯一生效发布指针。
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
     * Agent 业务ID
     */
    private Long agentId;

    /**
     * 系统自动生成的编码
     */
    private String agentCode;

    /**
     * 后台展示名称
     */
    private String agentName;

    /**
     * 聊天前端事件展示别称，留空回退 Agent 名称
     */
    private String displayAlias;

    /**
     * 主子类型
     */
    private AgentType agentType;

    /**
     * 启停状态
     */
    private ConfigStatus status;

    /**
     * 最新配置版本ID
     */
    private Long latestVersionId;

    /**
     * 最新配置版本号
     */
    private Integer latestVersionNo;

    /**
     * 最新配置版本就绪状态
     */
    private ConfigReadiness latestReadiness;

    /**
     * 当前生效的 Master 发布定格快照ID，子 Agent 恒为空
     */
    private Long currentReleaseId;

    /**
     * 编辑修订号
     */
    private Long revision;

    /**
     * 逻辑删除时间，未删除为空
     */
    private Instant deletedAt;

    /**
     * 创建人ID
     */
    private Long creatorId;

    /**
     * 更新人ID
     */
    private Long lastUpdaterId;

    /**
     * 更新时间
     */
    @TableField(insertStrategy = FieldStrategy.NEVER)
    private Instant lastUpdateTime;
}
