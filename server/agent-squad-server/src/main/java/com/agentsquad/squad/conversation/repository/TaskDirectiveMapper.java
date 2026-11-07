package com.agentsquad.squad.conversation.repository;

import com.agentsquad.squad.domain.entity.TaskDirectiveDO;
import com.agentsquad.squad.domain.entity.TaskDirectiveTargetDO;
import com.agentsquad.squad.domain.entity.TaskDirectiveFactDO;
import com.agentsquad.squad.domain.entity.TaskGoalStateDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 用户目标修改的
 * <p>
 * 调用方先锁会话和实际所有者；状态、版本和安全点检查均使用锁后当前读，不用旧快照决定是否应用。
 */
@Mapper
public interface TaskDirectiveMapper {
    TaskDirectiveDO lock(@Param("userId") String userId, @Param("directiveId") String directiveId);

    TaskDirectiveDO findByKey(@Param("userId") String userId, @Param("scopeId") String scopeId, @Param("key") String key);

    List<TaskDirectiveDO> applied(@Param("userId") String userId, @Param("taskId") String taskId, @Param("revision") long revision);

    int insert(@Param("row") TaskDirectiveDO row, @Param("now") LocalDateTime now);

    TaskGoalStateDO goalState(@Param("userId") String userId, @Param("taskId") String taskId);

    List<String> pending(@Param("userId") String userId, @Param("taskId") String taskId);

    List<TaskDirectiveTargetDO> due(@Param("now") LocalDateTime now);

    int defer(@Param("userId") String userId, @Param("directiveId") String directiveId, @Param("next") LocalDateTime next);

    int waiting(@Param("userId") String userId, @Param("directiveId") String directiveId,
                @Param("reason") com.agentsquad.squad.common.enums.conversation.TaskDirectiveWaitReason reason);

    List<TaskDirectiveDO> pageInConversation(@Param("userId") String userId,
                                              @Param("conversationId") String conversationId,
                                              @Param("generation") long generation,
                                              @Param("first") long first, @Param("last") long last);

    int bindCommand(@Param("userId") String userId, @Param("directiveId") String directiveId, @Param("commandId") String commandId);

    int finish(@Param("userId") String userId, @Param("directiveId") String directiveId,
               @Param("status") String status, @Param("version") Long version, @Param("revision") Long revision,
               @Param("code") String code, @Param("message") String message);

    int applyGoal(@Param("userId") String userId, @Param("taskId") String taskId,
                  @Param("version") long version, @Param("revision") long revision, @Param("input") String input);

    int clearCandidate(@Param("userId") String userId, @Param("taskId") String taskId);

    List<String> openOperations(@Param("userId") String userId, @Param("scopeId") String scopeId);

    List<String> openInteractions(@Param("userId") String userId, @Param("scopeId") String scopeId);

    List<TaskDirectiveFactDO> latestFacts(@Param("userId") String userId, @Param("taskId") String taskId);

    Long lastReplacement(@Param("userId") String userId, @Param("taskId") String taskId);

    default List<TaskDirectiveDO> effectiveApplied(String userId, String taskId) {
        Long revision = lastReplacement(userId, taskId);
        return applied(userId, taskId, revision == null ? 0 : revision);
    }

    List<String> pendingControlIds(@Param("userId") String userId, @Param("taskId") String taskId);

    default int pendingControls(String userId, String taskId) {
        return pendingControlIds(userId, taskId).size();
    }
}
