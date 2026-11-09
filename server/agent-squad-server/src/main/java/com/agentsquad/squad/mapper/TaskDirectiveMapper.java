package com.agentsquad.squad.mapper;

import com.agentsquad.squad.enums.TaskDirectiveWaitReason;
import com.agentsquad.squad.model.po.TaskDirectivePO;
import com.agentsquad.squad.model.bo.TaskDirectiveTargetBO;
import com.agentsquad.squad.model.bo.TaskDirectiveFactBO;
import com.agentsquad.squad.model.bo.TaskGoalStateBO;
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
    TaskDirectivePO lock(@Param("userId") Long userId, @Param("directiveId") Long directiveId);

    TaskDirectivePO findByKey(@Param("userId") Long userId, @Param("scopeId") Long scopeId, @Param("key") String key);

    List<TaskDirectivePO> applied(@Param("userId") Long userId, @Param("taskId") Long taskId, @Param("revision") long revision);

    int insert(@Param("row") TaskDirectivePO row, @Param("now") LocalDateTime now);

    TaskGoalStateBO goalState(@Param("userId") Long userId, @Param("taskId") Long taskId);

    List<Long> pending(@Param("userId") Long userId, @Param("taskId") Long taskId);

    List<TaskDirectiveTargetBO> due(@Param("now") LocalDateTime now);

    int defer(@Param("userId") Long userId, @Param("directiveId") Long directiveId, @Param("next") LocalDateTime next);

    int waiting(@Param("userId") Long userId, @Param("directiveId") Long directiveId,
                @Param("reason") TaskDirectiveWaitReason reason);

    List<TaskDirectivePO> pageInConversation(@Param("userId") Long userId,
                                             @Param("conversationId") Long conversationId,
                                             @Param("generation") long generation,
                                             @Param("first") long first, @Param("last") long last);

    int bindCommand(@Param("userId") Long userId, @Param("directiveId") Long directiveId, @Param("commandId") Long commandId);

    int finish(@Param("userId") Long userId, @Param("directiveId") Long directiveId,
               @Param("status") String status, @Param("version") Long version, @Param("revision") Long revision,
               @Param("code") String code, @Param("message") String message);

    int applyGoal(@Param("userId") Long userId, @Param("taskId") Long taskId,
                  @Param("version") long version, @Param("revision") long revision, @Param("input") String input);

    int clearCandidate(@Param("userId") Long userId, @Param("taskId") Long taskId);

    List<Long> openOperations(@Param("userId") Long userId, @Param("scopeId") Long scopeId);

    List<Long> openInteractions(@Param("userId") Long userId, @Param("scopeId") Long scopeId);

    List<TaskDirectiveFactBO> latestFacts(@Param("userId") Long userId, @Param("taskId") Long taskId);

    Long lastReplacement(@Param("userId") Long userId, @Param("taskId") Long taskId);

    default List<TaskDirectivePO> effectiveApplied(Long userId, Long taskId) {
        Long revision = lastReplacement(userId, taskId);
        return applied(userId, taskId, revision == null ? 0 : revision);
    }

    List<Long> pendingControlIds(@Param("userId") Long userId, @Param("taskId") Long taskId);

    default int pendingControls(Long userId, Long taskId) {
        return pendingControlIds(userId, taskId).size();
    }
}
