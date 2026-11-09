package com.agentsquad.squad.mapper;

import com.agentsquad.squad.model.po.ExecutionActivityPO;
import com.agentsquad.squad.model.dto.ActivityHistoryDTO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 执行活动持久访问
 * <p>
 * 同一绑定的活动在原生事件消费事务内更新，版本和原生序号同时防止迟到事件回退。
 */
@Mapper
public interface ExecutionActivityMapper {
    Long masterBinding(@Param("userId") Long userId, @Param("scopeId") Long scopeId);

    Long sourceMessage(@Param("userId") Long userId, @Param("scopeId") Long scopeId);

    ExecutionActivityPO find(@Param("userId") Long userId, @Param("bindingId") Long bindingId,
                             @Param("sourceKey") String sourceKey);

    int insert(ExecutionActivityPO row);

    int update(ExecutionActivityPO row);

    List<ExecutionActivityPO> activeRun(@Param("userId") Long userId, @Param("bindingId") Long bindingId,
                                        @Param("runId") String runId);

    List<ExecutionActivityPO> recent(@Param("userId") Long userId, @Param("conversationId") Long conversationId,
                                     @Param("generation") long generation, @Param("limit") int limit);

    List<ExecutionActivityPO> history(@Param("userId") Long userId, @Param("conversationId") Long conversationId,
                                      @Param("generation") long generation, @Param("query") ActivityHistoryDTO query,
                                      @Param("limit") int limit);
}
