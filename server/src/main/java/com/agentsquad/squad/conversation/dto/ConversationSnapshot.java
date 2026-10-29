package com.agentsquad.squad.conversation.dto;

import com.agentsquad.squad.artifact.ArtifactView;
import com.agentsquad.squad.presentation.dto.Interaction;

import java.util.List;

/**
 * 会话一致前缀快照。
 * <p>
 * 包含分页消息、在途执行与对话内交互；cursor 属于会话，不能与任务游标混用。
 */
public record ConversationSnapshot(ConversationView conversation, List<MessageView> messages,
                                   Long nextBeforeSeq, List<ResponseExecutionView> activeExecutions,
                                   TaskResponse activeTask, List<Interaction> interactions,
                                   List<ArtifactView> deliverables, List<ExecutionFailureView> failures,
                                   List<String> operationIds, List<ResponseExecutionView> executions,
                                   List<com.agentsquad.squad.presentation.domain.ConversationTaskState> taskStates, String cursor) {
}
