package com.agentsquad.squad.v2.converter;

import com.agentsquad.squad.v2.bo.BuiltinToolBO;
import com.agentsquad.squad.v2.bo.ConfigUsageBO;
import com.agentsquad.squad.v2.bo.HttpToolSpecBO;
import com.agentsquad.squad.v2.bo.ToolOptionBO;
import com.agentsquad.squad.v2.bo.ToolRunResultBO;
import com.agentsquad.squad.v2.bo.ToolSummaryBO;
import com.agentsquad.squad.v2.bo.VersionSummaryBO;
import com.agentsquad.squad.v2.dto.HttpToolSpecDTO;
import com.agentsquad.squad.v2.po.ToolVersionPO;
import com.agentsquad.squad.v2.vo.BuiltinToolOptionVO;
import com.agentsquad.squad.v2.vo.ConfigUsageVO;
import com.agentsquad.squad.v2.vo.HttpToolSpecVO;
import com.agentsquad.squad.v2.vo.ToolOptionVO;
import com.agentsquad.squad.v2.vo.ToolTestRunVO;
import com.agentsquad.squad.v2.vo.ToolVO;
import com.agentsquad.squad.v2.vo.ToolVersionVO;
import com.agentsquad.squad.v2.vo.VersionSummaryVO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * 工具配置转换
 * <p>
 * 把工具目录、配置版本、引用关系、可选实现和试运行结果投影为管理接口模型；MCP 服务名称由服务层补充。
 */
@Mapper(componentModel = "spring", uses = McpServerConfigConverter.class)
public interface ToolConfigConverter {
    ToolVO toView(ToolSummaryBO summary);

    @Mapping(target = "mcpServerName", ignore = true)
    ToolVersionVO toView(ToolVersionPO version);

    HttpToolSpecVO toView(HttpToolSpecBO spec);

    VersionSummaryVO toView(VersionSummaryBO summary);

    ConfigUsageVO toView(ConfigUsageBO usage);

    BuiltinToolOptionVO toView(BuiltinToolBO builtin);

    ToolOptionVO toView(ToolOptionBO option);

    ToolTestRunVO toView(ToolRunResultBO result);

    HttpToolSpecBO toSpec(HttpToolSpecDTO spec);
}
