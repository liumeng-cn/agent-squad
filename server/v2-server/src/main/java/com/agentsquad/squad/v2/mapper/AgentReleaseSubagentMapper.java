package com.agentsquad.squad.v2.mapper;

import com.agentsquad.squad.v2.po.AgentReleaseSubagentPO;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 发布定格 Sub 委派快照访问
 */
@Mapper
public interface AgentReleaseSubagentMapper extends BaseMapper<AgentReleaseSubagentPO> {
    int insertBatch(@Param("items") List<AgentReleaseSubagentPO> items);
}
