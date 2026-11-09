package com.agentsquad.squad.domain.entity;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * 技能包内的文本资料。
 * <p>
 * 表达已规范化的相对路径与内容，独立于HTTP请求响应和持久化行模型。
 */
@Getter
@Setter
@ToString
@EqualsAndHashCode
@NoArgsConstructor
@AllArgsConstructor
public class SkillTextFileDO {
    /** 技能文件相对路径 */
    private String path;
    /** 技能文件内容 */
    private String content;
}
