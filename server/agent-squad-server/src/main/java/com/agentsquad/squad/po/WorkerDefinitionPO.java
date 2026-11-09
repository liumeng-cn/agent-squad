package com.agentsquad.squad.po;

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
public class WorkerDefinitionPO {
    /** 存储主键ID */
    private long id;
    /** 能力定义业务ID */
    private String definitionId;
    /** 稳定能力键 */
    private String workerKey;
    /** 不可变能力版本 */
    private String workerVersion;
    /** 状态[ENABLED,DISABLED,REVOKED] */
    private WorkerDefinitionStatus status;
    /** 状态乐观版本 */
    private long version;
    /** 不可变能力定义 */
    private String definitionJson;
    /** 发布幂等键 */
    private String publishKey;
    /** 发布内容SHA256摘要 */
    private String payloadHash;
    /** 最近一次状态修改幂等键 */
    private String lastStatusKey;
    /** 最近一次状态修改主体ID */
    private String lastStatusActor;
    /** 最近一次状态修改内容摘要 */
    private String lastStatusHash;
}
