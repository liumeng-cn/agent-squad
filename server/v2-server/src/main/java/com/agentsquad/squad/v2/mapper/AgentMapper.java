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
 * 简单读写使用 BaseMapper；摘要联查、引用查询、切换生效版本和启停按修订号条件更新。
 */
@Mapper
public interface AgentMapper extends BaseMapper<AgentPO> {
    List<AgentSummaryBO> selectSummaries(@Param("agentId") Long agentId);

    List<ConfigUsageBO> selectUsages(@Param("agentId") long agentId);

    int switchVersion(AgentPO agent);

    int updateProfile(AgentPO agent);

    int updateStatus(AgentPO agent);

    /**
     * 以条件更新确认 Agent 仍启用，并持有行锁到事务结束，使委派登记与停用操作串行
     */
    int confirmEnabled(@Param("agentId") long agentId);
}
