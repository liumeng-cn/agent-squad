package com.agentsquad.squad.v2.mapper;

import com.agentsquad.squad.v2.po.ToolVersionPO;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * 工具版本访问
 * <p>
 * 使用 Mapper 维护目录和配置版本。
 */
@Mapper
public interface ToolVersionMapper extends BaseMapper<ToolVersionPO> {
    List<ToolVersionPO> usableVersions();

    int nextVersionNo(long toolId);

    int updateDraft(ToolVersionPO version);

    int publish(ToolVersionPO version);
}
