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
 * 用户可见事件的数据库查询结果，按任务序号回放
 */
@Getter
@Setter
@ToString
@EqualsAndHashCode
@NoArgsConstructor
@AllArgsConstructor
@TableName("sq_task_event")
public class EventPO {
    /**
     * 目标内事件序号
     */
    private long eventSeq;
    /**
     * 目标ID
     */
    private String taskId;
    /**
     * 公开事件类型
     */
    private String eventType;
    /**
     * 公开事件载荷
     */
    private String payloadJson;
    /**
     * 发生时间
     */
    private LocalDateTime occurredAt;
}
