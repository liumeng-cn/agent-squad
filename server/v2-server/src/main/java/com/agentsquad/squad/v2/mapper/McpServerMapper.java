package com.agentsquad.squad.v2.mapper;

import com.agentsquad.squad.v2.po.McpServerPO;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * MCP连接目录访问
 * <p>
 * 使用 Mapper 维护目录和配置版本。
 */
@Mapper
public interface McpServerMapper extends BaseMapper<McpServerPO> {
}
