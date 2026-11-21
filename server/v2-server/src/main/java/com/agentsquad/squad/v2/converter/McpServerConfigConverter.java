package com.agentsquad.squad.v2.converter;

import com.agentsquad.squad.v2.po.McpServerPO;
import com.agentsquad.squad.v2.po.McpServerVersionPO;
import com.agentsquad.squad.v2.vo.McpServerVO;
import com.agentsquad.squad.v2.vo.McpServerVersionVO;
import org.mapstruct.Mapper;

/**
 * MCP连接视图转换
 * <p>
 * 映射目录与版本的管理视图。
 */
@Mapper(componentModel = "spring")
public interface McpServerConfigConverter {
    McpServerVO toView(McpServerPO catalog);

    McpServerVersionVO toView(McpServerVersionPO version);
}
