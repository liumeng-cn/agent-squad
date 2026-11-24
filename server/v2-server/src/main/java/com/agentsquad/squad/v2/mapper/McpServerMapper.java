package com.agentsquad.squad.v2.mapper;

import com.agentsquad.squad.v2.bo.ConfigUsageBO;
import com.agentsquad.squad.v2.bo.McpServerSummaryBO;
import com.agentsquad.squad.v2.po.McpServerPO;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * MCP 服务目录访问
 * <p>
 * 简单读写使用 BaseMapper；摘要联查、引用查询和带修订号的条件更新写在 Mapper XML。
 */
@Mapper
public interface McpServerMapper extends BaseMapper<McpServerPO> {
    List<McpServerSummaryBO> selectSummaries(@Param("mcpServerId") Long mcpServerId);

    List<ConfigUsageBO> selectUsages(@Param("mcpServerId") long mcpServerId);

    int updateLatest(McpServerPO server);

    int updateStatus(McpServerPO server);

    int softDelete(McpServerPO server);

    int confirmEnabledReady(@Param("mcpServerId") long mcpServerId);

    String selectMaxCodeByPrefix(@Param("prefix") String prefix);
}
