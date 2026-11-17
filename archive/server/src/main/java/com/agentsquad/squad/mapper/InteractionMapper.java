package com.agentsquad.squad.mapper;

import com.agentsquad.squad.model.po.InteractionPO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 持久交互 ；框架请求按 (user, binding, requestId) 唯一绑定，答复带版本 CAS
 */
@Mapper
public interface InteractionMapper {

    int insert(@Param("interactionId") Long interactionId, @Param("userId") Long userId,
               @Param("taskId") Long taskId, @Param("jobId") Long jobId,
               @Param("bindingId") Long bindingId, @Param("operationId") Long operationId,
               @Param("origin") String origin, @Param("frameworkRequestId") String frameworkRequestId,
               @Param("kind") String kind, @Param("question") String question,
               @Param("answerSchemaJson") String answerSchemaJson,
               @Param("planVersion") long planVersion, @Param("requestHash") String requestHash,
               @Param("connectionRevision") long connectionRevision,
               @Param("expiresAt") LocalDateTime expiresAt,
               @Param("creatorId") Long creatorId, @Param("creatorName") String creatorName);

    InteractionPO findByFrameworkRequest(@Param("userId") Long userId,
                                         @Param("bindingId") Long bindingId,
                                         @Param("frameworkRequestId") String frameworkRequestId);

    InteractionPO findOwned(@Param("userId") Long userId,
                            @Param("interactionId") Long interactionId);

    List<InteractionPO> listByTask(@Param("userId") Long userId, @Param("taskId") Long taskId,
                                   @Param("cursorId") Long cursorId, @Param("limit") int limit);

    List<Long> findOpenIds(@Param("userId") Long userId, @Param("taskId") Long taskId);

    int countOpenByKind(@Param("userId") Long userId, @Param("taskId") Long taskId,
                        @Param("kind") String kind);

    Long cursorIdOf(@Param("userId") Long userId, @Param("interactionId") Long interactionId);

    int markResponded(@Param("userId") Long userId,
                      @Param("interactionId") Long interactionId,
                      @Param("expectedVersion") long expectedVersion,
                      @Param("responseJson") String responseJson,
                      @Param("updaterName") String updaterName);

    int markResolvedByFramework(@Param("userId") Long userId,
                                @Param("bindingId") Long bindingId,
                                @Param("frameworkRequestId") String frameworkRequestId,
                                @Param("responseJson") String responseJson);

    List<Long> findOpenByScope(@Param("userId") Long userId, @Param("scopeId") Long scopeId);

    int countOpenByScopeKind(@Param("userId") Long userId, @Param("scopeId") Long scopeId,
                             @Param("kind") String kind);

    int cancelOpenByTask(@Param("userId") Long userId, @Param("taskId") Long taskId);

    int cancelOpenByScope(@Param("userId") Long userId, @Param("scopeId") Long scopeId);
}
