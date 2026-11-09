package com.agentsquad.squad.model.po;

import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * 用户目标数据库查询结果；原生 Turn 和 Run 不属于此对象
 */
@Getter
@Setter
@ToString
@EqualsAndHashCode
@NoArgsConstructor
@AllArgsConstructor
@TableName("sq_goal_task")
public class TaskPO {
    /**
     * 存储主键ID
     */
    private long id;
    /**
     * 目标业务ID
     */
    private String taskId;
    /**
     * 资源归属用户ID
     */
    private String userId;
    /**
     * 所属对话ID
     */
    private String conversationId;
    /**
     * 产品状态
     */
    private String status;
    /**
     * 业务版本
     */
    private long version;
    /**
     * 计划版本
     */
    private long planVersion;
    /**
     * 用户可见最终答案版本
     */
    private long answerRevision;
    /**
     * 取消请求时间，用于判断是否允许提交完成结果
     */
    private LocalDateTime cancelRequestedAt;
    /**
     * 当前恢复阻塞原因；不保存原生异常正文
     */
    private String blockedReason;
    /**
     * 创建时间
     */
    private LocalDateTime createTime;
    /**
     * 最后更新时间
     */
    private LocalDateTime lastUpdateTime;
}
