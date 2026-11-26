package com.agentsquad.squad.v2.converter;

import com.agentsquad.squad.v2.po.ChatConvPO;
import com.agentsquad.squad.v2.vo.ConvSummaryVO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * 对话目录转换
 * <p>
 * 将业务目录字段转换为公开摘要，仅转换 convs 表内的目录字段。
 */
@Mapper(componentModel = "spring")
public interface ChatConvConverter {

    @Mapping(target = "agentCode", source = "agentDefinitionId")
    @Mapping(target = "agentDisplayName", ignore = true)
    @Mapping(target = "createdAt", source = "createTime")
    @Mapping(target = "archived", expression = "java(conv.getArchivedAt() != null)")
    ConvSummaryVO toSummary(ChatConvPO conv);
}
