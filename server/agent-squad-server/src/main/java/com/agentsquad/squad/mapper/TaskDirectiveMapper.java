package com.agentsquad.squad.mapper;

import com.agentsquad.squad.enums.TaskDirectiveWaitReason;
import com.agentsquad.squad.po.TaskDirectivePO;
import com.agentsquad.squad.bo.TaskDirectiveTargetBO;
import com.agentsquad.squad.bo.TaskDirectiveFactBO;
import com.agentsquad.squad.bo.TaskGoalStateBO;
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
    TaskDirectivePO lock(@Param("userId") String userId, @Param("directiveId") String directiveId);

    TaskDirectivePO findByKey(@Param("userId") String userId, @Param("scopeId") String scopeId, @Param("key") String key);

    List<TaskDirectivePO> applied(@Param("userId") String userId, @Param("taskId") String taskId, @Param("revision") long revision);

    int insert(@Param("row") TaskDirectivePO row, @Param("now") LocalDateTime now);

    TaskGoalStateBO goalState(@Param("userId") String userId, @Param("taskId") String taskId);

    List<String> pending(@Param("userId") String userId, @Param("taskId") String taskId);

    List<TaskDirectiveTargetBO> due(@Param("now") LocalDateTime now);

    int defer(@Param("userId") String userId, @Param("directiveId") String directiveId, @Param("next") LocalDateTime next);

    int waiting(@Param("userId") String userId, @Param("directiveId") String directiveId,
                @Param("reason") TaskDirectiveWaitReason reason);

    List<TaskDirectivePO> pageInConversation(@Param("userId") String userId,
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

    List<TaskDirectiveFactBO> latestFacts(@Param("userId") String userId, @Param("taskId") String taskId);

    Long lastReplacement(@Param("userId") String userId, @Param("taskId") String taskId);

    default List<TaskDirectivePO> effectiveApplied(String userId, String taskId) {
        Long revision = lastReplacement(userId, taskId);
        return applied(userId, taskId, revision == null ? 0 : revision);
    }

    List<String> pendingControlIds(@Param("userId") String userId, @Param("taskId") String taskId);

    default int pendingControls(String userId, String taskId) {
        return pendingControlIds(userId, taskId).size();
    }
}
