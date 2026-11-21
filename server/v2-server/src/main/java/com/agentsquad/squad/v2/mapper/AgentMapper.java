package com.agentsquad.squad.v2.mapper;

import com.agentsquad.squad.v2.po.AgentPO;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * Agent目录访问
 * <p>
 * 负责Agent目录的数据库读写。
 */
@Mapper
public interface AgentMapper extends BaseMapper<AgentPO> {
    int switchVersion(AgentPO agent);
}
