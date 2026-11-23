package com.agentsquad.squad.v2.mapper;

import com.agentsquad.squad.v2.bo.ToolOptionBO;
import com.agentsquad.squad.v2.bo.VersionSummaryBO;
import com.agentsquad.squad.v2.po.ToolVersionPO;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 工具版本访问
 * <p>
 * 草稿编辑与发布按“草稿状态 + 修订号”条件更新；可用版本查询同时检查工具与 MCP 依赖状态。
 */
@Mapper
public interface ToolVersionMapper extends BaseMapper<ToolVersionPO> {
    int nextVersionNo(@Param("toolId") long toolId);

    List<VersionSummaryBO> selectSummaries(@Param("toolId") long toolId);

    List<ToolOptionBO> selectUsableOptions();

    int updateDraft(ToolVersionPO version);

    int publish(ToolVersionPO version);
}
