package com.agentsquad.squad.model.po;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * 待投递命令及幂等收据的数据库查询结果
 */
@Getter
@Setter
@ToString
@EqualsAndHashCode
@NoArgsConstructor
@AllArgsConstructor
@TableName("sq_command")
public class CommandPO {
    /**
     * 命令业务ID
     */
    private Long commandId;
    /**
     * 资源归属用户ID
     */
    private Long userId;
    /**
     * 所属目标ID；删除类命令可为空
     */
    private Long taskId;
    /**
     * 原生会话绑定ID；删除类命令可为空
     */
    private Long bindingId;
    /**
     * 规范化请求SHA256
     */
    private String payloadHash;
    /**
     * PENDING/DELIVERING/DELIVERED/APPLIED/BLOCKED
     */
    private String status;
    /**
     * AgentScope 原生回合ID
     */
    private String nativeTurnId;
    /**
     * AgentScope 原生运行ID
     */
    private String nativeRunId;
    /**
     * AgentScope 提交时状态
     */
    private String nativeStatus;
}
