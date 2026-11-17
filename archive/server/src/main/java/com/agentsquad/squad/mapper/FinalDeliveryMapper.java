package com.agentsquad.squad.mapper;

import com.agentsquad.squad.model.po.FinalDeliveryPO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 用户请求的最终答复记录读写
 * <p>
 * 记录由回复执行或目标任务负责发布最终答复，以及答复是否已发布或关闭。
 * 修改时由业务事务持有行锁；负责发布的执行记录由数据库关联确定，不从模型正文推断。
 */
@Mapper
public interface FinalDeliveryMapper {
    FinalDeliveryPO lockCurrent(@Param("userId") Long userId, @Param("messageId") Long messageId);

    int insert(@Param("deliveryId") Long deliveryId, @Param("userId") Long userId,
               @Param("messageId") Long messageId, @Param("generation") long generation,
               @Param("executionId") Long executionId);

    int transfer(@Param("userId") Long userId, @Param("messageId") Long messageId,
                 @Param("generation") long generation, @Param("executionId") Long executionId,
                 @Param("taskId") Long taskId);

    int publish(@Param("userId") Long userId, @Param("messageId") Long messageId,
                @Param("generation") long generation, @Param("answerId") Long answerId);

    int close(@Param("userId") Long userId, @Param("messageId") Long messageId,
              @Param("generation") long generation);
}
