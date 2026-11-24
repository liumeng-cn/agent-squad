package com.agentsquad.squad.v2.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * Master 发布定格的 Sub 委派快照
 * <p>
 * 映射 sq_agent_release_subagent，记录发布时刻定格的子 Agent 配置版本及委派元数据。
 */
@Data
@TableName(value = "sq_agent_release_subagent", schema = "squad_chat")
public class AgentReleaseSubagentPO {
    /**
     * 内部主键
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 所属发布快照业务ID
     */
    private Long releaseId;

    /**
     * 定格的子 Agent 业务ID
     */
    private Long subagentId;

    /**
     * 定格的子 Agent 配置版本ID
     */
    private Long subagentVersionId;

    /**
     * 发布时定格的子 Agent 名称
     */
    private String subagentName;

    /**
     * 定格的委派名称
     */
    private String delegationName;

    /**
     * 定格的委派描述
     */
    private String delegationDescription;

    /**
     * 定格的前端展示别称
     */
    private String displayAlias;

    /**
     * 展示顺序
     */
    private Integer sortNo;

    /**
     * 创建人ID
     */
    private Long creatorId;

    /**
     * 更新人ID
     */
    private Long lastUpdaterId;
}
