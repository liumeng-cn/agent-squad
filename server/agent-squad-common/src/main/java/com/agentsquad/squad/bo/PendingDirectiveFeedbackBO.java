package com.agentsquad.squad.bo;

/**
 * 当前目标待处理修改的受控内容。
 * <p>
 * 仅返回给已授权的目标协调工具，不放入异常消息或日志。
 */
public record PendingDirectiveFeedbackBO(Long directiveId, long expectedVersion, String kind, String content) {
}
