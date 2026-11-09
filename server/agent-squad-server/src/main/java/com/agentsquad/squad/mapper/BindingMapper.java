package com.agentsquad.squad.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;

/**
 * AgentScope 会话发现目录 ；固定标识由后续 Bridge 使用
 */
@Mapper
public interface BindingMapper {

    int insertMaster(@Param("bindingId") String bindingId, @Param("userId") String userId,
                     @Param("taskId") String taskId, @Param("agentId") String agentId,
                     @Param("sessionId") String sessionId,
                     @Param("nextProjectionAt") LocalDateTime nextProjectionAt);

    int insertWorker(@Param("bindingId") String bindingId, @Param("userId") String userId,
                     @Param("taskId") String taskId, @Param("agentId") String agentId,
                     @Param("sessionId") String sessionId,
                     @Param("nextProjectionAt") LocalDateTime nextProjectionAt);

    com.agentsquad.squad.domain.enums.LocalSessionState localSessionState(
            @Param("userId") String userId, @Param("bindingId") String bindingId);

    java.util.List<String> taskBindingIds(@Param("userId") String userId, @Param("taskId") String taskId);

    String findMasterBindingId(@Param("userId") String userId, @Param("taskId") String taskId);

    int insertResponse(@Param("bindingId") String bindingId, @Param("userId") String userId,
                       @Param("scopeId") String scopeId, @Param("agentId") String agentId,
                       @Param("sessionId") String sessionId, @Param("nextProjectionAt") LocalDateTime nextProjectionAt);
}
