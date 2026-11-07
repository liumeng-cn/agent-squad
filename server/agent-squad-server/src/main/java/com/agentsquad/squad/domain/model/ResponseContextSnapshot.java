package com.agentsquad.squad.domain.model;

import com.agentsquad.squad.domain.entity.ResponseFailureFactDO;
import com.agentsquad.squad.domain.entity.ResponseTaskStatusFactDO;

import java.util.List;

/**
 * 普通回复的固定会话前缀。
 * <p>
 * 同一执行重投复用相同正文，Worker 的局部上下文不会从此对象继承。
 */
public record ResponseContextSnapshot(String conversationId, long generation, String sourceMessageId,
                                      long throughSequence, List<ResponseContextMessage> messages, ResponseTaskFacts activeTask, List<ResponseTaskStatusFactDO> recentTasks,
                                      List<ResponseFailureFactDO> recentFailures) {
}
