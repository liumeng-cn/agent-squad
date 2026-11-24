package com.agentsquad.squad.v2.bo;

import com.agentsquad.squad.v2.po.AgentVersionSubagentPO;
import lombok.Data;

import java.util.List;

/**
 * Agent 配置保存时校验通过的绑定结果
 */
@Data
public class AgentDraftBindingsBO {
    /**
     * 校验通过的工具业务ID列表
     */
    private List<Long> toolIds = List.of();

    /**
     * 校验通过的子 Agent 委派行
     */
    private List<AgentVersionSubagentPO> delegations = List.of();
}
