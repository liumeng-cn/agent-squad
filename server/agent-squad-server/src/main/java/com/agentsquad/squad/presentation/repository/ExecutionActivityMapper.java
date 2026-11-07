package com.agentsquad.squad.presentation.repository;

import com.agentsquad.squad.presentation.domain.ExecutionActivityRow;
import com.agentsquad.squad.presentation.dto.ActivityHistoryQuery;
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
    String masterBinding(@Param("userId") String userId, @Param("scopeId") String scopeId);

    String sourceMessage(@Param("userId") String userId, @Param("scopeId") String scopeId);

    ExecutionActivityRow find(@Param("userId") String userId, @Param("bindingId") String bindingId,
                              @Param("sourceKey") String sourceKey);

    int insert(ExecutionActivityRow row);

    int update(ExecutionActivityRow row);

    List<ExecutionActivityRow> activeRun(@Param("userId") String userId, @Param("bindingId") String bindingId,
                                         @Param("runId") String runId);

    List<ExecutionActivityRow> recent(@Param("userId") String userId, @Param("conversationId") String conversationId,
                                      @Param("generation") long generation, @Param("limit") int limit);

    List<ExecutionActivityRow> history(@Param("userId") String userId, @Param("conversationId") String conversationId,
                                       @Param("generation") long generation, @Param("query") ActivityHistoryQuery query,
                                       @Param("limit") int limit);
}
