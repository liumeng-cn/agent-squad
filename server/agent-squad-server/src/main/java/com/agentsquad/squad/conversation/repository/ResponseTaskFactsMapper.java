package com.agentsquad.squad.conversation.repository;

import com.agentsquad.squad.conversation.domain.ResponseJobFact;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 会话当前目标的受权进度查询边界。
 * <p>
 * 调用方持有会话与目标锁；当前读只取当前计划，不拼接旧计划结果或Worker原生日志。
 */
@Mapper
public interface ResponseTaskFactsMapper {
    Long scopeGeneration(@Param("userId") String userId, @Param("taskId") String taskId);

    List<ResponseJobFact> jobs(@Param("userId") String userId, @Param("taskId") String taskId,
                               @Param("planVersion") long planVersion);

    List<com.agentsquad.squad.conversation.domain.ResponseTaskStatusFact> recentTasks(@Param("userId") String userId,
            @Param("conversationId") String conversationId,@Param("generation") long generation,@Param("through") long through);

    List<com.agentsquad.squad.conversation.domain.ResponseFailureFact> recentFailures(@Param("userId") String userId,
            @Param("conversationId") String conversationId,@Param("generation") long generation,@Param("through") long through);
}
