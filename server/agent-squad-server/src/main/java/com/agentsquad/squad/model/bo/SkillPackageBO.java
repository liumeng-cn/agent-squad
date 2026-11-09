package com.agentsquad.squad.model.bo;

import com.agentsquad.squad.model.po.SkillTextFilePO;

import java.util.List;

/**
 * 已规范化的技能发布包。
 * <p>
 * 校验结果按路径排序，摘要涵盖元数据与完整文件文本。
 */
public record SkillPackageBO(String name, String version, String description, List<String> requiredTools,
                           List<SkillTextFilePO> files, String contentHash) {
}
