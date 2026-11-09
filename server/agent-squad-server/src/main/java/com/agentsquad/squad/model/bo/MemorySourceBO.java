package com.agentsquad.squad.model.bo;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * 显式记忆授权来源。
 * <p>
 * 通过当前回复绑定定位持久用户消息，工具不能指定其他消息。
 */
@Getter
@Setter
@ToString
@EqualsAndHashCode
@NoArgsConstructor
@AllArgsConstructor
public class MemorySourceBO {
    /**
     * 消息业务ID
     */
    private String messageId;
    /**
     * 用户消息或授权助手正文
     */
    private String content;
}
