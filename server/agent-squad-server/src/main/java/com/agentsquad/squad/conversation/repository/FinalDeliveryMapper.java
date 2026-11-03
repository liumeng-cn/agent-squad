package com.agentsquad.squad.conversation.repository;

import com.agentsquad.squad.conversation.domain.FinalDeliveryRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 最终交付位持久边界。
 * <p>
 * 所有修改由业务事务持锁执行，不按模型正文推断交付归属。
 */
@Mapper
public interface FinalDeliveryMapper {
    FinalDeliveryRow lockCurrent(@Param("userId") String userId, @Param("messageId") String messageId);

    int insert(@Param("deliveryId") String deliveryId, @Param("userId") String userId,
               @Param("messageId") String messageId, @Param("generation") long generation,
               @Param("executionId") String executionId);

    int transfer(@Param("userId") String userId, @Param("messageId") String messageId,
                 @Param("generation") long generation, @Param("executionId") String executionId,
                 @Param("taskId") String taskId);

    int publish(@Param("userId") String userId, @Param("messageId") String messageId,
                @Param("generation") long generation, @Param("answerId") String answerId);

    int close(@Param("userId") String userId, @Param("messageId") String messageId,
              @Param("generation") long generation);
}
