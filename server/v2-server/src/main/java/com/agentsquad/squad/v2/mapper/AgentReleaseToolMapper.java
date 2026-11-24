package com.agentsquad.squad.v2.mapper;

import com.agentsquad.squad.v2.po.AgentReleaseToolPO;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 发布定格工具与 MCP 快照访问
 */
@Mapper
public interface AgentReleaseToolMapper extends BaseMapper<AgentReleaseToolPO> {
    int insertBatch(@Param("items") List<AgentReleaseToolPO> items);
}
