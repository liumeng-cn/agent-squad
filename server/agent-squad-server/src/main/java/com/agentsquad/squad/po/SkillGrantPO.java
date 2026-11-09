package com.agentsquad.squad.po;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * 技能授权数据库记录
 * <p>
 * 启停控制新执行是否可选，安全撤销由版本授权代际控制。
 */
@Getter
@Setter
@ToString
@EqualsAndHashCode
@NoArgsConstructor
@AllArgsConstructor
public class SkillGrantPO {
    /** 授权用户ID */
    private String userId;
    /** 被授权技能版本ID */
    private String skillVersionId;
    /** 是否允许新执行选择 */
    private boolean enabled;
    /** 授权乐观版本 */
    private long version;
}
