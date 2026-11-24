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
 * Agent 配置版本访问
 * <p>
 * 每次保存插入新配置版本；绑定查询联表返回上游工具与子 Agent 的最新配置版本及可用状态。
 */
@Mapper
public interface AgentVersionMapper extends BaseMapper<AgentVersionPO> {
    int nextVersionNo(@Param("agentId") long agentId);

    List<VersionSummaryBO> selectSummaries(@Param("agentId") long agentId);

    List<AgentToolBindingBO> selectToolBindings(@Param("agentVersionId") long agentVersionId);

    List<AgentToolBindingBO> selectToolCandidates(@Param("toolIds") Collection<Long> toolIds);

    List<AgentDelegationBO> selectDelegations(@Param("agentVersionId") long agentVersionId);

    List<AgentDelegationBO> selectDelegationCandidates(@Param("subagentIds") Collection<Long> subagentIds);

    List<SubagentOptionBO> selectSubagentOptions();
}
