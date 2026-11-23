package com.agentsquad.squad.v2.mapper;

import com.agentsquad.squad.v2.bo.ConfigUsageBO;
import com.agentsquad.squad.v2.bo.ToolSummaryBO;
import com.agentsquad.squad.v2.po.ToolPO;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 工具目录访问
 * <p>
 * 简单读写使用 BaseMapper；摘要联查、引用查询和带修订号的条件更新写在 Mapper XML。
 */
@Mapper
public interface ToolMapper extends BaseMapper<ToolPO> {
    List<ToolSummaryBO> selectSummaries(@Param("toolId") Long toolId);

    List<ConfigUsageBO> selectUsages(@Param("toolId") long toolId);

    int updateProfile(ToolPO tool);

    int updateStatus(ToolPO tool);

    /**
     * 以条件更新确认工具仍启用，并持有行锁到事务结束，使引用登记与停用操作串行
     */
    int confirmEnabled(@Param("toolId") long toolId);
}
