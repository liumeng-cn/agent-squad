package com.agentsquad.squad.v2.mapper;

import com.agentsquad.squad.v2.po.AgentVersionToolPO;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * Agent 版本工具绑定访问
 * <p>
 * 草稿保存时整体替换绑定，批量写入使用显式 SQL。
 */
@Mapper
public interface AgentVersionToolMapper extends BaseMapper<AgentVersionToolPO> {
    int insertBatch(@Param("bindings") List<AgentVersionToolPO> bindings);
}
