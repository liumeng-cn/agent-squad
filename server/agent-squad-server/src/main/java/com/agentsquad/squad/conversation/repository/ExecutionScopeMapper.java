package com.agentsquad.squad.conversation.repository;

import com.agentsquad.squad.conversation.domain.ExecutionScopeRow;
import com.agentsquad.squad.conversation.domain.ExecutionOwnerRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 执行归属查询边界。
 * <p>
 * 必须连同会话归属、代际和删除状态读取，不能仅凭客户端提供的 scopeId 获得执行权限。
 */
@Mapper
public interface ExecutionScopeMapper {

    ExecutionScopeRow findActiveOwned(@Param("userId") String userId, @Param("scopeId") String scopeId);

    int insertAccount(@Param("userId") String userId, @Param("accountId") String accountId,
                      @Param("profileKey") String profileKey, @Param("limit") long limit);

    int insertScope(@Param("scopeId") String scopeId, @Param("userId") String userId,
                    @Param("conversationId") String conversationId, @Param("generation") long generation,
                    @Param("kind") String kind, @Param("ownerId") String ownerId, @Param("accountId") String accountId);

    int linkTaskScope(@Param("userId") String userId, @Param("taskId") String taskId, @Param("scopeId") String scopeId);

    ExecutionScopeRow findOwned(@Param("userId") String userId, @Param("scopeId") String scopeId);

    String findBindingBySession(@Param("userId") String userId, @Param("sessionId") String sessionId);

    String findScopeBySession(@Param("userId") String userId, @Param("sessionId") String sessionId);

    String findScopeByBinding(@Param("userId") String userId, @Param("bindingId") String bindingId);

    String findScopeByTask(@Param("userId") String userId, @Param("taskId") String taskId);

    ExecutionOwnerRow lockTaskOwner(@Param("userId") String userId, @Param("ownerId") String ownerId,
                                   @Param("scopeId") String scopeId);

    ExecutionOwnerRow lockResponseOwner(@Param("userId") String userId, @Param("ownerId") String ownerId,
                                       @Param("scopeId") String scopeId);

    int queueTaskAfterInteraction(@Param("userId") String userId, @Param("scopeId") String scopeId);

    int queueResponseAfterInteraction(@Param("userId") String userId, @Param("scopeId") String scopeId);
}
