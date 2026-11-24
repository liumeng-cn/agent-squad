package com.agentsquad.squad.v2.mapper;

import com.agentsquad.squad.v2.bo.McpServerOptionBO;
import com.agentsquad.squad.v2.bo.VersionSummaryBO;
import com.agentsquad.squad.v2.po.McpServerVersionPO;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * MCP 配置版本访问
 * <p>
 * 每次保存插入新配置版本；候选项仅返回未删除、启用且最新配置就绪的 MCP 服务。
 */
@Mapper
public interface McpServerVersionMapper extends BaseMapper<McpServerVersionPO> {
    int nextVersionNo(@Param("mcpServerId") long mcpServerId);

    List<VersionSummaryBO> selectSummaries(@Param("mcpServerId") long mcpServerId);

    List<McpServerOptionBO> selectUsableOptions();
}
