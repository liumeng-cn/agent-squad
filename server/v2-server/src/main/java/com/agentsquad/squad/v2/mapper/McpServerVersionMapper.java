package com.agentsquad.squad.v2.mapper;

import com.agentsquad.squad.v2.bo.McpServerOptionBO;
import com.agentsquad.squad.v2.bo.VersionSummaryBO;
import com.agentsquad.squad.v2.po.McpServerVersionPO;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * MCP 连接版本访问
 * <p>
 * 草稿编辑与发布按“草稿状态 + 修订号”条件更新，影响行数不为 1 时由调用方返回冲突。
 */
@Mapper
public interface McpServerVersionMapper extends BaseMapper<McpServerVersionPO> {
    int nextVersionNo(@Param("mcpServerId") long mcpServerId);

    List<VersionSummaryBO> selectSummaries(@Param("mcpServerId") long mcpServerId);

    List<McpServerOptionBO> selectUsableOptions();

    int updateDraft(McpServerVersionPO version);

    int publish(McpServerVersionPO version);
}
