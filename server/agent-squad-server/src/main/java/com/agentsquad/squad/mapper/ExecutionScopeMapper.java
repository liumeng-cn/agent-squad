package com.agentsquad.squad.mapper;

import com.agentsquad.squad.domain.entity.ExecutionScopeDO;
import com.agentsquad.squad.domain.entity.ExecutionOwnerDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 执行范围：维护执行关联，并校验数据归属和关联有效性
 * <p>
 * 必须连同会话归属、代际和删除状态读取，不能仅凭客户端提供的 scopeId 获得执行权限。
 */
@Mapper
public interface ExecutionScopeMapper {
    int saveTrace(@Param("userId") String userId, @Param("scopeId") String scopeId,
                  @Param("traceparent") String traceparent, @Param("tracestate") String tracestate);

    int inheritTrace(@Param("userId") String userId, @Param("scopeId") String scopeId,
                     @Param("parentScopeId") String parentScopeId);

    java.util.Map<String, String> commandTrace(@Param("commandId") String commandId);

    java.util.Map<String, String> bindingTrace(@Param("userId") String userId, @Param("bindingId") String bindingId);

    java.util.Map<String, String> sessionTrace(@Param("userId") String userId, @Param("sessionId") String sessionId);

    ExecutionScopeDO findActiveOwned(@Param("userId") String userId, @Param("scopeId") String scopeId);

    int insertAccount(@Param("userId") String userId, @Param("accountId") String accountId,
                      @Param("profileKey") String profileKey, @Param("limit") long limit);

    int insertScope(@Param("scopeId") String scopeId, @Param("userId") String userId,
                    @Param("conversationId") String conversationId, @Param("generation") long generation,
                    @Param("kind") String kind, @Param("ownerId") String ownerId, @Param("accountId") String accountId);

    int linkTaskScope(@Param("userId") String userId, @Param("taskId") String taskId, @Param("scopeId") String scopeId);

    ExecutionScopeDO findOwned(@Param("userId") String userId, @Param("scopeId") String scopeId);

    String findBindingBySession(@Param("userId") String userId, @Param("sessionId") String sessionId);

    String findScopeBySession(@Param("userId") String userId, @Param("sessionId") String sessionId);

    String findScopeByBinding(@Param("userId") String userId, @Param("bindingId") String bindingId);

    String findScopeByTask(@Param("userId") String userId, @Param("taskId") String taskId);

    ExecutionOwnerDO lockTaskOwner(@Param("userId") String userId, @Param("ownerId") String ownerId,
                                   @Param("scopeId") String scopeId);

    ExecutionOwnerDO lockResponseOwner(@Param("userId") String userId, @Param("ownerId") String ownerId,
                                       @Param("scopeId") String scopeId);

    int queueTaskAfterInteraction(@Param("userId") String userId, @Param("scopeId") String scopeId);

    int queueResponseAfterInteraction(@Param("userId") String userId, @Param("scopeId") String scopeId);
}
