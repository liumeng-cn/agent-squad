package com.agentsquad.squad.model.bo;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * 已提交消息的上下文查询投影。
 * <p>
 * 只含公开角色正文，附件由独立归属关系补充，不读取内部原生日志。
 */
@Getter
@Setter
@ToString
@EqualsAndHashCode
@NoArgsConstructor
@AllArgsConstructor
public class ResponseHistoryBO {
    /**
     * 消息业务ID
     */
    private String messageId;
    /**
     * 回复序号
     */
    private long sequence;
    /**
     * 消息角色[USER,ASSISTANT]
     */
    private String role;
    /**
     * 回复正文
     */
    private String text;
    /**
     * 正文替换版本
     */
    private long revision;
}
