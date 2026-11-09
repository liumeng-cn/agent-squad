package com.agentsquad.squad.mapper;

import com.agentsquad.squad.model.bo.ResponseJobFactBO;
import com.agentsquad.squad.model.bo.ResponseFailureFactBO;
import com.agentsquad.squad.model.bo.ResponseTaskStatusFactBO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 查询当前用户有权访问的会话目标进度
 * <p>
 * 调用方持有会话与目标锁；当前读只取当前计划，不拼接旧计划结果或Worker原生日志。
 */
@Mapper
public interface ResponseTaskFactsMapper {
    Long scopeGeneration(@Param("userId") String userId, @Param("taskId") String taskId);

    List<ResponseJobFactBO> jobs(@Param("userId") String userId, @Param("taskId") String taskId,
                                 @Param("planVersion") long planVersion);

    List<ResponseTaskStatusFactBO> recentTasks(@Param("userId") String userId,
                                               @Param("conversationId") String conversationId, @Param("generation") long generation, @Param("through") long through);

    List<ResponseFailureFactBO> recentFailures(@Param("userId") String userId,
                                               @Param("conversationId") String conversationId, @Param("generation") long generation, @Param("through") long through);
}
