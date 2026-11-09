package com.agentsquad.squad.domain.entity;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * 技能版本数据库记录
 * <p>
 * 记录用户归属、不可变元数据与安全撤销代际，不能当作接口 DTO。
 */
@Getter
@Setter
@ToString
@EqualsAndHashCode
@NoArgsConstructor
@AllArgsConstructor
public class SkillVersionDO {
    /** 存储主键ID */
    private long id;
    /** 技能版本业务ID */
    private String skillVersionId;
    /** 所属用户ID */
    private String userId;
    /** 显示名称 */
    private String name;
    /** 版本号 */
    private String version;
    /** 技能目录描述 */
    private String description;
    /** 规范化文本包SHA256摘要 */
    private String contentHash;
    /** 使用技能所需工具名称列表 */
    private String requiredToolsJson;
    /** 版本状态[ENABLED,REVOKED] */
    private String status;
    /** 安全授权版本号 */
    private long authorizationRevision;
}
