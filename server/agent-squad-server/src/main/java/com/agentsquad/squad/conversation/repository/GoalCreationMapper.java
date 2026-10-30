package com.agentsquad.squad.conversation.repository;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 独立目标来源关系的持久边界。
 * <p>
 * 创建事务中固定来源消息和回复执行，后续目标推进不依赖原回复保持运行。
 */
@Mapper
public interface GoalCreationMapper {
    int linkSource(@Param("userId") String userId, @Param("taskId") String taskId,
                   @Param("messageId") String messageId, @Param("executionId") String executionId);
}
