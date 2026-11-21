package com.agentsquad.squad.v2.mapper;

import com.agentsquad.squad.v2.po.AgentVersionSubagentPO;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 子Agent绑定访问
 * <p>
 * 负责子Agent绑定的数据库读写。
 */
@Mapper
public interface AgentVersionSubagentMapper extends BaseMapper<AgentVersionSubagentPO> {
}
