package com.agentsquad.squad.model.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.agentsquad.squad.enums.WorkerDefinitionStatus;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * Worker 能力定义数据库记录
 * <p>
 * 定义正文发布后不可修改；状态和乐观版本可单独变更。
 */
@Getter
@Setter
@ToString
@EqualsAndHashCode
@NoArgsConstructor
@AllArgsConstructor
@TableName("sq_worker_definition")
public class WorkerDefinitionPO {
    /**
     * 存储主键ID
     */
    @TableId(type = IdType.AUTO)
    private long id;
    /**
     * 能力定义业务ID
     */
    @TableField("definition_id")
    private String definitionId;
    /**
     * 稳定能力键
     */
    @TableField("worker_key")
    private String workerKey;
    /**
     * 不可变能力版本
     */
    @TableField("worker_version")
    private String workerVersion;
    /**
     * 状态[ENABLED,DISABLED,REVOKED]
     */
    private WorkerDefinitionStatus status;
    /**
     * 状态乐观版本
     */
    private long version;
    /**
     * 不可变能力定义
     */
    @TableField("definition_json")
    private String definitionJson;
    /**
     * 发布幂等键
     */
    @TableField("publish_key")
    private String publishKey;
    /**
     * 发布内容SHA256摘要
     */
    @TableField("payload_hash")
    private String payloadHash;
    /**
     * 最近一次状态修改幂等键
     */
    @TableField("last_status_key")
    private String lastStatusKey;
    /**
     * 最近一次状态修改主体ID
     */
    @TableField("last_status_actor")
    private String lastStatusActor;
    /**
     * 最近一次状态修改内容摘要
     */
    @TableField("last_status_hash")
    private String lastStatusHash;
}
