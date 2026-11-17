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
 * 委派节点的数据库记录
 * <p>
 * 一节点一行；status 由平台状态机推进，input_json 为固定局部输入，result_json 为最终结构化结果，
 * cancel_requested_at 记录新计划替代时的取消屏障。
 */
@Getter
@Setter
@ToString
@EqualsAndHashCode
@NoArgsConstructor
@AllArgsConstructor
@TableName("sq_worker_job")
public class WorkerJobPO {
    /**
     * 存储主键ID
     */
    private long id;
    /**
     * 委派节点业务ID
     */
    private Long jobId;
    /**
     * 资源归属用户ID
     */
    private Long userId;
    /**
     * 所属目标ID
     */
    private Long taskId;
    /**
     * 所属计划版本
     */
    private long planVersion;
    /**
     * 计划内节点键
     */
    private String nodeKey;
    /**
     * 能力键
     */
    private String workerKey;
    /**
     * 能力版本
     */
    private String workerVersion;
    /**
     * BLOCKED/READY/RUNNING/SUCCEEDED/PARTIAL/FAILED/WAITING_INPUT/WAITING_APPROVAL/RECOVERY_BLOCKED/RECONCILING/CANCELLING/CANCELLED/SUPERSEDED
     */
    private String status;
    /**
     * 依赖节点键数组
     */
    private String dependsOnJson;
    /**
     * 固定局部输入
     */
    private String inputJson;
    /**
     * 结果候选（非最终结果）
     */
    private String resultCandidateJson;
    /**
     * 最终结构化结果
     */
    private String resultJson;
    /**
     * 运行时绑定ID
     */
    private Long bindingId;
    /**
     * 结果复用来源JobID
     */
    private Long reusedFromJobId;
    /**
     * 取消屏障时间；新计划替代在途旧Job时写入
     */
    private LocalDateTime cancelRequestedAt;
    /**
     * 业务版本
     */
    private long version;
}
