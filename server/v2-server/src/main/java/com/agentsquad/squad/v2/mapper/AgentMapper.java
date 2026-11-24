package com.agentsquad.squad.v2.mapper;

import com.agentsquad.squad.v2.bo.AgentSummaryBO;
import com.agentsquad.squad.v2.bo.ConfigUsageBO;
import com.agentsquad.squad.v2.po.AgentPO;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * Agent 目录访问
 * <p>
 * 简单读写使用 BaseMapper；摘要联查、引用查询、切换 Master 发布指针和启停删除按修订号条件更新。
 */
@Mapper
public interface AgentMapper extends BaseMapper<AgentPO> {
    List<AgentSummaryBO> selectSummaries(@Param("agentId") Long agentId);

    List<ConfigUsageBO> selectUsages(@Param("agentId") long agentId);

    int updateLatest(AgentPO agent);

    int switchRelease(AgentPO agent);

    int updateStatus(AgentPO agent);

    int softDelete(AgentPO agent);

    int confirmEnabledReady(@Param("agentId") long agentId);

    String selectMaxCodeByPrefix(@Param("prefix") String prefix);
}
