package com.agentsquad.squad.converter;

import com.agentsquad.squad.model.po.SkillTextFilePO;
import com.agentsquad.squad.model.vo.SkillFileVO;
import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;
import org.mapstruct.ReportingPolicy;

import java.util.List;

/**
 * 技能文件转换器
 *
 * <p>将技能文件的持久化记录转换为技能详情的公开字段。</p>
 */
@Mapper(componentModel = MappingConstants.ComponentModel.SPRING, unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface SkillFileConverter {

    SkillFileVO toView(SkillTextFilePO file);

    List<SkillFileVO> toViews(List<SkillTextFilePO> files);
}
