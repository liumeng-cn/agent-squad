package com.agentsquad.squad.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 执行资料失效后的持久阻塞写入。
 * <p>
 * 不清理未知外部操作和预算；仅阻止旧上下文继续派发与发布正文。
 */
@Mapper
public interface ExecutionRestrictionMapper {
    int failResponse(@Param("userId") Long userId, @Param("scopeId") Long scopeId);

    int blockTask(@Param("userId") Long userId, @Param("scopeId") Long scopeId);

    int blockJobs(@Param("userId") Long userId, @Param("taskId") Long taskId);

    int blockCommands(@Param("userId") Long userId, @Param("scopeId") Long scopeId);
}
