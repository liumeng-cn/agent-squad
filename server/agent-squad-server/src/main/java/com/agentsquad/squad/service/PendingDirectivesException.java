package com.agentsquad.squad.service;

import com.agentsquad.squad.common.exception.RuntimeBusinessException;
import com.agentsquad.squad.common.exception.RuntimeExceptionCode;
import com.agentsquad.squad.domain.dto.PendingDirectiveFeedback;

import java.util.List;

/**
 * 计划发布被待处理目标修改阻断。
 * <p>
 * 异常消息只含安全原因，受控工具单独提取修改事实，避免堆栈日志暴露用户正文。
 */
public class PendingDirectivesException extends RuntimeBusinessException {
    private final String taskId;
    private final List<PendingDirectiveFeedback> directives;

    public PendingDirectivesException(String taskId, List<PendingDirectiveFeedback> directives) {
        super(RuntimeExceptionCode.CONFLICT, "目标存在待处理修改，须先完成协调");
        this.taskId = taskId;
        this.directives = List.copyOf(directives);
    }

    public String taskId() {
        return taskId;
    }

    public List<PendingDirectiveFeedback> directives() {
        return directives;
    }
}
