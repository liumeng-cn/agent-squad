package com.agentsquad.squad.interaction.repository;

import com.agentsquad.squad.interaction.domain.InteractionRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 持久交互 SQL 边界；框架请求按 (user, binding, requestId) 唯一绑定，答复带版本 CAS。
 */
@Mapper
public interface InteractionMapper {

    int insert(@Param("interactionId") String interactionId, @Param("userId") String userId,
               @Param("taskId") String taskId, @Param("jobId") String jobId,
               @Param("bindingId") String bindingId, @Param("operationId") String operationId,
               @Param("origin") String origin, @Param("frameworkRequestId") String frameworkRequestId,
               @Param("kind") String kind, @Param("question") String question,
               @Param("answerSchemaJson") String answerSchemaJson,
               @Param("planVersion") long planVersion, @Param("requestHash") String requestHash,
               @Param("connectionRevision") long connectionRevision,
               @Param("expiresAt") LocalDateTime expiresAt,
               @Param("creatorId") String creatorId, @Param("creatorName") String creatorName);

    InteractionRow findByFrameworkRequest(@Param("userId") String userId,
                                          @Param("bindingId") String bindingId,
                                          @Param("frameworkRequestId") String frameworkRequestId);

    InteractionRow findOwned(@Param("userId") String userId,
                             @Param("interactionId") String interactionId);

    List<InteractionRow> listByTask(@Param("userId") String userId, @Param("taskId") String taskId,
                                    @Param("cursorId") Long cursorId, @Param("limit") int limit);

    List<String> findOpenIds(@Param("userId") String userId, @Param("taskId") String taskId);

    int countOpenByKind(@Param("userId") String userId, @Param("taskId") String taskId,
                        @Param("kind") String kind);

    Long cursorIdOf(@Param("userId") String userId, @Param("interactionId") String interactionId);

    int markResponded(@Param("userId") String userId,
                      @Param("interactionId") String interactionId,
                      @Param("expectedVersion") long expectedVersion,
                      @Param("responseJson") String responseJson,
                      @Param("updaterName") String updaterName);

    int markResolvedByFramework(@Param("userId") String userId,
                                @Param("bindingId") String bindingId,
                                @Param("frameworkRequestId") String frameworkRequestId,
                                @Param("responseJson") String responseJson);

    List<String> findOpenByScope(@Param("userId") String userId, @Param("scopeId") String scopeId);

    int countOpenByScopeKind(@Param("userId") String userId, @Param("scopeId") String scopeId,
                            @Param("kind") String kind);
}
