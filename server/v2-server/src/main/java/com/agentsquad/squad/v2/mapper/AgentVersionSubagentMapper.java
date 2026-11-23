package com.agentsquad.squad.v2.mapper;

import com.agentsquad.squad.v2.po.AgentVersionSubagentPO;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * Agent 版本委派关系访问
 * <p>
 * 草稿保存时整体替换委派，批量写入使用显式 SQL。
 */
@Mapper
public interface AgentVersionSubagentMapper extends BaseMapper<AgentVersionSubagentPO> {
    int insertBatch(@Param("delegations") List<AgentVersionSubagentPO> delegations);
}
