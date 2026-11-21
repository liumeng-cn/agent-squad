package com.agentsquad.squad.v2.converter;

import com.agentsquad.squad.v2.po.ToolPO;
import com.agentsquad.squad.v2.po.ToolVersionPO;
import com.agentsquad.squad.v2.vo.ToolVO;
import com.agentsquad.squad.v2.vo.ToolVersionVO;
import org.mapstruct.Mapper;

/**
 * 工具视图转换
 * <p>
 * 映射目录与版本的管理视图。
 */
@Mapper(componentModel = "spring")
public interface ToolConfigConverter {
    ToolVO toView(ToolPO catalog);

    ToolVersionVO toView(ToolVersionPO version);
}
