package com.agentsquad.squad.v2.mapper;

import com.agentsquad.squad.v2.bo.AgentDelegationBO;
import com.agentsquad.squad.v2.bo.AgentReleaseSummaryBO;
import com.agentsquad.squad.v2.bo.AgentToolBindingBO;
import com.agentsquad.squad.v2.po.AgentReleasePO;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * Master 发布定格快照访问
 * <p>
 * 查询发布序号、发布历史列表及按发布上下文逐层穿透的定格绑定关系。
 */
@Mapper
public interface AgentReleaseMapper extends BaseMapper<AgentReleasePO> {
    int nextReleaseNo(@Param("masterAgentId") long masterAgentId);

    List<AgentReleaseSummaryBO> selectSummaries(@Param("masterAgentId") long masterAgentId);

    List<AgentToolBindingBO> selectReleaseTools(@Param("releaseId") long releaseId,
                                                @Param("ownerAgentId") long ownerAgentId);

    List<AgentDelegationBO> selectReleaseSubagents(@Param("releaseId") long releaseId);
}
