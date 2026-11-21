package com.agentsquad.squad.v2.mapper;

import com.agentsquad.squad.v2.po.McpServerVersionPO;
import com.agentsquad.squad.v2.vo.McpServerVersionOptionVO;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * MCP连接版本访问
 * <p>
 * 使用 Mapper 维护目录和配置版本。
 */
@Mapper
public interface McpServerVersionMapper extends BaseMapper<McpServerVersionPO> {
    List<McpServerVersionOptionVO> usableOptions();

    int nextVersionNo(long mcpServerId);

    int updateDraft(McpServerVersionPO version);

    int publish(McpServerVersionPO version);
}
