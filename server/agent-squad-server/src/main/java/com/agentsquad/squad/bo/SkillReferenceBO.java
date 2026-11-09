package com.agentsquad.squad.bo;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * 执行固定的技能引用。
 * <p>
 * 记录实际版本、摘要及授权代际，不受后续发布或普通停用影响。
 */
@Getter
@Setter
@ToString
@EqualsAndHashCode
@NoArgsConstructor
@AllArgsConstructor
public class SkillReferenceBO {
    /** 技能版本业务ID */
    private String skillVersionId;
    /** 显示名称 */
    private String name;
    /** 版本号 */
    private String version;
    /** 技能目录描述 */
    private String description;
    /** 规范化文本包SHA256摘要 */
    private String contentHash;
    /** 安全授权版本号 */
    private long authorizationRevision;
}
