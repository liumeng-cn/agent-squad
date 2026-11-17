package com.agentsquad.squad.mapper;

import com.agentsquad.squad.enums.LocalSessionState;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;

/**
 * AgentScope 会话发现目录 ；固定标识由后续 Bridge 使用
 */
@Mapper
public interface BindingMapper {

    int insertMaster(@Param("bindingId") Long bindingId, @Param("userId") Long userId,
                     @Param("taskId") Long taskId, @Param("agentId") String agentId,
                     @Param("sessionId") String sessionId,
                     @Param("nextProjectionAt") LocalDateTime nextProjectionAt);

    int insertWorker(@Param("bindingId") Long bindingId, @Param("userId") Long userId,
                     @Param("taskId") Long taskId, @Param("agentId") String agentId,
                     @Param("sessionId") String sessionId,
                     @Param("nextProjectionAt") LocalDateTime nextProjectionAt);

    LocalSessionState localSessionState(
            @Param("userId") Long userId, @Param("bindingId") Long bindingId);

    java.util.List<Long> taskBindingIds(@Param("userId") Long userId, @Param("taskId") Long taskId);

    Long findMasterBindingId(@Param("userId") Long userId, @Param("taskId") Long taskId);

    int insertResponse(@Param("bindingId") Long bindingId, @Param("userId") Long userId,
                       @Param("scopeId") Long scopeId, @Param("agentId") String agentId,
                       @Param("sessionId") String sessionId, @Param("nextProjectionAt") LocalDateTime nextProjectionAt);
}
