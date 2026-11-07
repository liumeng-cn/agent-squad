package com.agentsquad.squad.domain.response;

import com.agentsquad.squad.domain.dto.ExecutionActivityView;
import com.agentsquad.squad.domain.dto.ExecutionFailureView;
import com.agentsquad.squad.domain.dto.MessageView;
import com.agentsquad.squad.domain.dto.ResponseExecutionView;
import com.agentsquad.squad.domain.dto.TaskDirectiveView;
import com.agentsquad.squad.domain.dto.TaskResponse;

import com.agentsquad.squad.domain.entity.ConversationTaskStateDO;

import java.util.List;

/**
 * 会话一致前缀快照。
 * <p>
 * 包含分页消息、在途执行与对话内交互；cursor 属于会话，不能与任务游标混用。
 */
public record ConversationSnapshotResponse(ConversationViewResponse conversation, List<MessageView> messages,
                                   Long nextBeforeSeq, List<ResponseExecutionView> activeExecutions,
                                   TaskResponse activeTask, List<InteractionResponse> interactions,
                                   List<ArtifactResponse> deliverables, List<ExecutionFailureView> failures,
                                   List<String> operationIds, List<ResponseExecutionView> executions,
                                   List<ConversationTaskStateDO> taskStates, List<TaskDirectiveView> directives,
                                   List<ExecutionActivityView> activities, String cursor) {
}
