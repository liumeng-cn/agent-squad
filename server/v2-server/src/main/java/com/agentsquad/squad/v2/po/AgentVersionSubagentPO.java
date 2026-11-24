package com.agentsquad.squad.v2.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * Master 配置版本的 Sub 委派关联
 * <p>
 * 映射 sq_agent_version_subagent，按子 Agent 业务 ID 关联，配置态默认使用子 Agent 最新配置版本。
 */
@Data
@TableName(value = "sq_agent_version_subagent", schema = "squad_chat")
public class AgentVersionSubagentPO {
    /**
     * 内部主键
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 所属 Master 配置版本ID
     */
    private Long masterVersionId;

    /**
     * 关联的子 Agent 业务ID
     */
    private Long subagentId;

    /**
     * 主 Agent 内唯一的委派名称
     */
    private String delegationName;

    /**
     * 委派描述，留空使用子 Agent 职责描述
     */
    private String delegationDescription;

    /**
     * 委派展示别称，留空回退子 Agent 别称或名称
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
