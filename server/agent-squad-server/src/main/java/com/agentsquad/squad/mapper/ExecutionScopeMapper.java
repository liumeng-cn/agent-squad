package com.agentsquad.squad.mapper;

import com.agentsquad.squad.model.po.ExecutionScopePO;
import com.agentsquad.squad.model.bo.ExecutionOwnerBO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 执行范围：维护执行关联，并校验数据归属和关联有效性
 * <p>
 * 必须连同会话归属、代际和删除状态读取，不能仅凭客户端提供的 scopeId 获得执行权限。
 */
@Mapper
public interface ExecutionScopeMapper {
    int saveTrace(@Param("userId") Long userId, @Param("scopeId") Long scopeId,
                  @Param("traceparent") String traceparent, @Param("tracestate") String tracestate);

    int inheritTrace(@Param("userId") Long userId, @Param("scopeId") Long scopeId,
                     @Param("parentScopeId") Long parentScopeId);

    java.util.Map<String, String> commandTrace(@Param("commandId") Long commandId);

    java.util.Map<String, String> bindingTrace(@Param("userId") Long userId, @Param("bindingId") Long bindingId);

    java.util.Map<String, String> sessionTrace(@Param("userId") Long userId, @Param("sessionId") String sessionId);

    ExecutionScopePO findActiveOwned(@Param("userId") Long userId, @Param("scopeId") Long scopeId);

    int insertAccount(@Param("userId") Long userId, @Param("accountId") Long accountId,
                      @Param("profileKey") String profileKey, @Param("limit") long limit);

    int insertScope(@Param("scopeId") Long scopeId, @Param("userId") Long userId,
                    @Param("conversationId") Long conversationId, @Param("generation") long generation,
                    @Param("kind") String kind, @Param("ownerId") Long ownerId, @Param("accountId") Long accountId);

    int linkTaskScope(@Param("userId") Long userId, @Param("taskId") Long taskId, @Param("scopeId") Long scopeId);

    ExecutionScopePO findOwned(@Param("userId") Long userId, @Param("scopeId") Long scopeId);

    Long findBindingBySession(@Param("userId") Long userId, @Param("sessionId") String sessionId);

    Long findScopeBySession(@Param("userId") Long userId, @Param("sessionId") String sessionId);

    Long findScopeByBinding(@Param("userId") Long userId, @Param("bindingId") Long bindingId);

    Long findScopeByTask(@Param("userId") Long userId, @Param("taskId") Long taskId);

    ExecutionOwnerBO lockTaskOwner(@Param("userId") Long userId, @Param("ownerId") Long ownerId,
                                   @Param("scopeId") Long scopeId);

    ExecutionOwnerBO lockResponseOwner(@Param("userId") Long userId, @Param("ownerId") Long ownerId,
                                       @Param("scopeId") Long scopeId);

    int queueTaskAfterInteraction(@Param("userId") Long userId, @Param("scopeId") Long scopeId);

    int queueResponseAfterInteraction(@Param("userId") Long userId, @Param("scopeId") Long scopeId);
}
