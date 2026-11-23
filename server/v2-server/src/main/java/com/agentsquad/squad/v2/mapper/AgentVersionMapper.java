package com.agentsquad.squad.v2.mapper;

import com.agentsquad.squad.v2.bo.AgentDelegationBO;
import com.agentsquad.squad.v2.bo.AgentToolBindingBO;
import com.agentsquad.squad.v2.bo.SubagentOptionBO;
import com.agentsquad.squad.v2.bo.VersionSummaryBO;
import com.agentsquad.squad.v2.po.AgentVersionPO;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Collection;
import java.util.List;

/**
 * Agent 版本访问
 * <p>
 * 草稿编辑与发布按“草稿状态 + 修订号”条件更新；绑定查询联表返回工具和子 Agent 的可用状态。
 */
@Mapper
public interface AgentVersionMapper extends BaseMapper<AgentVersionPO> {
    int nextVersionNo(@Param("agentId") long agentId);

    List<VersionSummaryBO> selectSummaries(@Param("agentId") long agentId);

    List<AgentToolBindingBO> selectToolBindings(@Param("agentVersionId") long agentVersionId);

    List<AgentToolBindingBO> selectToolCandidates(@Param("toolVersionIds") Collection<Long> toolVersionIds);

    List<AgentDelegationBO> selectDelegations(@Param("agentVersionId") long agentVersionId);

    List<AgentDelegationBO> selectDelegationCandidates(@Param("subagentVersionIds") Collection<Long> subagentVersionIds);

    List<SubagentOptionBO> selectSubagentOptions();

    int updateDraft(AgentVersionPO version);

    int publish(AgentVersionPO version);
}
