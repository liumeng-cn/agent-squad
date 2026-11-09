package com.agentsquad.squad.model.bo;


import java.util.List;

/**
 * 普通回复的固定会话前缀。
 * <p>
 * 同一执行重投复用相同正文，Worker 的局部上下文不会从此对象继承。
 */
public record ResponseContextSnapshotBO(String conversationId, long generation, String sourceMessageId,
                                        long throughSequence, List<ResponseContextMessageBO> messages,
                                        ResponseTaskFactsBO activeTask, List<ResponseTaskStatusFactBO> recentTasks,
                                        List<ResponseFailureFactBO> recentFailures) {
}
