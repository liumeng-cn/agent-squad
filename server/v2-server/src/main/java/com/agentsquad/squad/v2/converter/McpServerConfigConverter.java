package com.agentsquad.squad.v2.converter;

import com.agentsquad.squad.v2.bo.ConfigUsageBO;
import com.agentsquad.squad.v2.bo.KeyValueBO;
import com.agentsquad.squad.v2.bo.McpRemoteToolBO;
import com.agentsquad.squad.v2.bo.McpServerOptionBO;
import com.agentsquad.squad.v2.bo.McpServerSummaryBO;
import com.agentsquad.squad.v2.bo.VersionSummaryBO;
import com.agentsquad.squad.v2.dto.KeyValueDTO;
import com.agentsquad.squad.v2.po.McpServerVersionPO;
import com.agentsquad.squad.v2.vo.ConfigUsageVO;
import com.agentsquad.squad.v2.vo.KeyValueVO;
import com.agentsquad.squad.v2.vo.McpRemoteToolVO;
import com.agentsquad.squad.v2.vo.McpServerOptionVO;
import com.agentsquad.squad.v2.vo.McpServerVO;
import com.agentsquad.squad.v2.vo.McpServerVersionVO;
import com.agentsquad.squad.v2.vo.VersionSummaryVO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

/**
 * MCP 服务配置转换
 * <p>
 * 把服务目录、版本、引用关系和远程工具投影为管理接口模型；业务ID在接口中以字符串传递。
 */
@Mapper(componentModel = "spring")
public interface McpServerConfigConverter {
    McpServerVO toView(McpServerSummaryBO summary);

    @Mapping(target = "headers", source = "requestHeaders")
    McpServerVersionVO toView(McpServerVersionPO version);

    VersionSummaryVO toView(VersionSummaryBO summary);

    ConfigUsageVO toView(ConfigUsageBO usage);

    McpRemoteToolVO toView(McpRemoteToolBO tool);

    McpServerOptionVO toView(McpServerOptionBO option);

    KeyValueVO toView(KeyValueBO header);

    List<KeyValueBO> toHeaders(List<KeyValueDTO> headers);
}
