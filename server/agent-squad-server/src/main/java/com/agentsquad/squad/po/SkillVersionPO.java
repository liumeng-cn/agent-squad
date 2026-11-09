package com.agentsquad.squad.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
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
@TableName("sq_skill_version")
public class SkillVersionPO {
    /** 存储主键ID */
    @TableId(type = IdType.AUTO)
    private long id;
    /** 技能版本业务ID */
    @TableField("skill_version_id")
    private String skillVersionId;
    /** 所属用户ID */
    @TableField("user_id")
    private String userId;
    /** 显示名称 */
    @TableField("skill_name")
    private String name;
    /** 版本号 */
    @TableField("skill_version")
    private String version;
    /** 技能目录描述 */
    private String description;
    /** 规范化文本包SHA256摘要 */
    @TableField("content_hash")
    private String contentHash;
    /** 使用技能所需工具名称列表 */
    @TableField("required_tools_json")
    private String requiredToolsJson;
    /** 版本状态[ENABLED,REVOKED] */
    private String status;
    /** 安全授权版本号 */
    @TableField("authorization_revision")
    private long authorizationRevision;
}
