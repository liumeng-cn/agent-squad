package com.agentsquad.squad.model.vo;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 技能文本文件响应
 *
 * <p>返回技能包内的相对路径与文本内容。</p>
 */
@Getter
@AllArgsConstructor
public class SkillFileVO {

    /** 包内相对路径 */
    private final String path;

    /** 文件文本内容 */
    private final String content;
}
