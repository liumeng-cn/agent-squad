package com.agentsquad.squad.v2.bo;

import com.agentsquad.squad.v2.enums.AgentType;
import com.agentsquad.squad.v2.enums.ConfigStatus;
import com.agentsquad.squad.v2.enums.VersionStatus;
import lombok.Data;

/**
 * 子 Agent 委派投影
 * <p>
 * 委派目标版本及其可用状态；usable 为子 Agent 类型正确、启用且版本已发布。
 */
@Data
public class AgentDelegationBO {
    /**
     * 子 Agent 版本ID
     */
    private Long subagentVersionId;

    /**
     * 子 Agent 业务ID
     */
    private Long subagentId;

    /**
     * 子 Agent 编码
     */
    private String subagentCode;

    /**
     * 子 Agent 名称
     */
    private String subagentName;

    /**
     * 目标 Agent 类型
     */
    private AgentType subagentType;

    /**
     * 子 Agent 启停状态
     */
    private ConfigStatus subagentStatus;

    /**
     * 该版本的职责描述
     */
    private String description;

    /**
     * 版本号
     */
    private Integer versionNo;

    /**
     * 版本状态
     */
    private VersionStatus versionStatus;

    /**
     * 子 Agent 当前生效版本号
     */
    private Integer currentVersionNo;

    /**
     * 委派名称
     */
    private String delegationName;

    /**
     * 委派说明
     */
    private String delegationDescription;

    /**
     * 是否可用
     */
    private boolean usable;
}
