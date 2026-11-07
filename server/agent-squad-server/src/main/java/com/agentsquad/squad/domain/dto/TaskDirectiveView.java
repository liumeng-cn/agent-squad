package com.agentsquad.squad.domain.dto;

import com.agentsquad.squad.common.enums.conversation.TaskDirectiveStatus;
import com.agentsquad.squad.common.enums.conversation.TaskDirectiveWaitReason;
import com.agentsquad.squad.domain.entity.TaskDirectiveRow;

/**
 * 用户要求修改的公开处理结果
 * <p>
 * 只公开来源、等待原因和实际应用版本，不包含模型归纳、原文材料及内部命令。
 */
public record TaskDirectiveView(String directiveId, String taskId, String sourceMessageId,
                                TaskDirectiveStatus status, TaskDirectiveWaitReason waitReason,
                                Long appliedVersion, Long goalRevision, String reasonCode, String reasonMessage) {
    /**
     * 已有历史受理记录没有等待原因时，保守显示等待安全处理点，不推测已经开始协调。
     */
    public static TaskDirectiveView from(TaskDirectiveRow row) {
        TaskDirectiveStatus status = TaskDirectiveStatus.valueOf(row.status());
        TaskDirectiveWaitReason wait = TaskDirectiveWaitReason.NONE;
        if (status == TaskDirectiveStatus.ACCEPTED) {
            wait = TaskDirectiveWaitReason.WAITING_SAFE_POINT;
            if (row.reasonCode() != null) {
                try {
                    wait = TaskDirectiveWaitReason.valueOf(row.reasonCode());
                } catch (IllegalArgumentException unknown) {
                    // 旧错误码不能伪造成已开始协调。
                }
            }
        }
        return new TaskDirectiveView(row.directiveId(), row.taskId(), row.sourceMessageId(), status, wait,
                row.appliedVersion(), row.appliedGoalRevision(), row.reasonCode(), row.reasonMessage());
    }
}
