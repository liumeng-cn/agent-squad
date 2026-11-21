package com.agentsquad.squad.v2.mapper;

import com.agentsquad.squad.v2.po.AgentVersionPO;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Collection;
import java.util.List;

/**
 * Agent版本访问
 * <p>
 * 负责Agent版本的数据库读写。
 */
@Mapper
public interface AgentVersionMapper extends BaseMapper<AgentVersionPO> {
    int nextVersionNo(Long agentId);

    int updateDraft(AgentVersionPO version);

    int publish(AgentVersionPO version);

    List<String> enabledToolNames(@Param("versionIds") Collection<Long> versionIds);
}
