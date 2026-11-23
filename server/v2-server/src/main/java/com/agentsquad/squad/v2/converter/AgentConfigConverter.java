package com.agentsquad.squad.v2.converter;

import com.agentsquad.squad.v2.bo.AgentDelegationBO;
import com.agentsquad.squad.v2.bo.AgentSummaryBO;
import com.agentsquad.squad.v2.bo.AgentToolBindingBO;
import com.agentsquad.squad.v2.bo.ConfigUsageBO;
import com.agentsquad.squad.v2.bo.SubagentOptionBO;
import com.agentsquad.squad.v2.bo.VersionSummaryBO;
import com.agentsquad.squad.v2.po.AgentVersionPO;
import com.agentsquad.squad.v2.vo.AgentConfigVO;
import com.agentsquad.squad.v2.vo.AgentConfigVersionVO;
import com.agentsquad.squad.v2.vo.AgentDelegationVO;
import com.agentsquad.squad.v2.vo.AgentToolBindingVO;
import com.agentsquad.squad.v2.vo.ConfigUsageVO;
import com.agentsquad.squad.v2.vo.SubagentOptionVO;
import com.agentsquad.squad.v2.vo.VersionSummaryVO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * Agent 配置转换
 * <p>
 * 把 Agent 目录、版本、绑定、引用关系和可委派选项投影为管理接口模型；绑定列表由服务层补充。
 */
@Mapper(componentModel = "spring")
public interface AgentConfigConverter {
    AgentConfigVO toView(AgentSummaryBO summary);

    @Mapping(target = "tools", ignore = true)
    @Mapping(target = "subagents", ignore = true)
    AgentConfigVersionVO toView(AgentVersionPO version);

    AgentToolBindingVO toView(AgentToolBindingBO binding);

    AgentDelegationVO toView(AgentDelegationBO delegation);

    VersionSummaryVO toView(VersionSummaryBO summary);

    ConfigUsageVO toView(ConfigUsageBO usage);

    SubagentOptionVO toView(SubagentOptionBO option);
}
