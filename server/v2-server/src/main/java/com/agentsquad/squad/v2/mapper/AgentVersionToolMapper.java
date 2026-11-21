package com.agentsquad.squad.v2.mapper;

import com.agentsquad.squad.v2.po.AgentVersionToolPO;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 工具绑定访问
 * <p>
 * 负责工具绑定的数据库读写。
 */
@Mapper
public interface AgentVersionToolMapper extends BaseMapper<AgentVersionToolPO> {
}
