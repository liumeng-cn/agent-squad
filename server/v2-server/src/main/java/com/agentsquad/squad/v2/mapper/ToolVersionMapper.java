package com.agentsquad.squad.v2.mapper;

import com.agentsquad.squad.v2.bo.ToolOptionBO;
import com.agentsquad.squad.v2.bo.VersionSummaryBO;
import com.agentsquad.squad.v2.po.ToolVersionPO;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 工具配置版本访问
 * <p>
 * 每次保存插入新配置版本；候选项仅返回未删除、启用且最新配置就绪的工具。
 */
@Mapper
public interface ToolVersionMapper extends BaseMapper<ToolVersionPO> {
    int nextVersionNo(@Param("toolId") long toolId);

    List<VersionSummaryBO> selectSummaries(@Param("toolId") long toolId);

    List<ToolOptionBO> selectUsableOptions();
}
