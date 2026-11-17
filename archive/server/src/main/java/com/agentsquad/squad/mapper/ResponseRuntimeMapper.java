package com.agentsquad.squad.mapper;

import com.agentsquad.squad.model.bo.ProjectionTargetBO;
import com.agentsquad.squad.model.po.ResponseRuntimePO;
import com.agentsquad.squad.model.bo.ReplyPreviewTargetBO;
import com.agentsquad.squad.enums.ResponseCompletionProtocol;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 回复生命周期、完成候选及公开消息的
 * <p>
 * 调用方先持有会话和回复所有者锁，再维护候选、命令与消息，保证重投幂等。
 */
@Mapper
public interface ResponseRuntimeMapper {

    String modelChoiceByBinding(@Param("userId") Long userId, @Param("bindingId") Long bindingId);

    String modelChoiceBySession(@Param("userId") Long userId, @Param("sessionId") String sessionId);

    ReplyPreviewTargetBO previewTarget(@Param("userId") Long userId, @Param("sessionId") String sessionId);

    ResponseCompletionProtocol completionProtocol(
            @Param("userId") Long userId, @Param("bindingId") Long bindingId);

    ResponseCompletionProtocol completionProtocolBySession(
            @Param("userId") Long userId, @Param("sessionId") String sessionId);

    ResponseRuntimePO lockByBinding(@Param("userId") Long userId, @Param("bindingId") Long bindingId);

    Long bindingForExecution(@Param("userId") Long userId, @Param("executionId") Long executionId);

    int status(@Param("userId") Long userId, @Param("bindingId") Long bindingId, @Param("status") String status);

    int propose(@Param("userId") Long userId, @Param("bindingId") Long bindingId,
                @Param("turnId") String turnId, @Param("json") String json, @Param("hash") String hash);

    int finish(@Param("userId") Long userId, @Param("bindingId") Long bindingId,
               @Param("status") String status, @Param("answerId") Long answerId);

    int correct(@Param("userId") Long userId, @Param("bindingId") Long bindingId);

    int recovering(@Param("userId") Long userId, @Param("bindingId") Long bindingId);

    int cancelling(@Param("userId") Long userId, @Param("bindingId") Long bindingId, @Param("now") LocalDateTime now);

    int cancelPending(@Param("userId") Long userId, @Param("bindingId") Long bindingId);

    List<Long> uncertainIds(@Param("userId") Long userId, @Param("scopeId") Long scopeId);

    List<Long> uncertainSandboxIds(@Param("userId") Long userId, @Param("scopeId") Long scopeId);

    default int uncertain(Long userId, Long scopeId) {
        return uncertainIds(userId, scopeId).size() + uncertainSandboxIds(userId, scopeId).size();
    }

    List<String> openKinds(@Param("userId") Long userId, @Param("scopeId") Long scopeId);

    default int openInteractions(Long userId, Long scopeId) {
        return openKinds(userId, scopeId).size();
    }

    int answer(@Param("messageId") Long messageId, @Param("userId") Long userId,
               @Param("conversationId") Long conversationId, @Param("sequence") long sequence,
               @Param("sourceMessageId") Long sourceMessageId, @Param("executionId") Long executionId,
               @Param("text") String text, @Param("now") LocalDateTime now);

    Long originalCommand(@Param("userId") Long userId, @Param("bindingId") Long bindingId);

    List<Long> activeBindings(@Param("userId") Long userId, @Param("conversationId") Long conversationId);

    List<ProjectionTargetBO> cancellationTargets(@Param("afterBinding") Long afterBinding);
}
