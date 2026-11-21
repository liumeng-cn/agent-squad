package com.agentsquad.squad.v2.converter;

import com.agentsquad.squad.v2.po.AgentPO;
import com.agentsquad.squad.v2.po.AgentVersionPO;
import com.agentsquad.squad.v2.po.AgentVersionSubagentPO;
import com.agentsquad.squad.v2.vo.AgentConfigVO;
import com.agentsquad.squad.v2.vo.AgentConfigVersionVO;
import com.agentsquad.squad.v2.vo.AgentDelegationVO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * Agent配置视图转换
 * <p>
 * 将目录、版本和绑定投影为管理接口响应。
 */
@Mapper(componentModel = "spring")
public interface AgentConfigConverter {
    AgentConfigVO toView(AgentPO agent);

    @Mapping(target = "toolVersionIds", ignore = true)
    @Mapping(target = "subagents", ignore = true)
    AgentConfigVersionVO toView(AgentVersionPO version);

    AgentDelegationVO toView(AgentVersionSubagentPO binding);
}
