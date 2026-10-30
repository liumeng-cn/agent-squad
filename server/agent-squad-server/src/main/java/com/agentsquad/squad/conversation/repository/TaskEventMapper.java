package com.agentsquad.squad.conversation.repository;

import com.agentsquad.squad.runtime.domain.EventRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 公开事件 SQL 边界；事件与受理命令在同一业务事务中提交。
 */
@Mapper
public interface TaskEventMapper {

    int insertAccepted(@Param("userId") String userId, @Param("taskId") String taskId,
                       @Param("producerKey") String producerKey, @Param("payloadJson") String payloadJson,
                       @Param("occurredAt") LocalDateTime occurredAt);

    int insertProjected(@Param("userId") String userId, @Param("taskId") String taskId,
                        @Param("sequence") long sequence, @Param("producerKey") String producerKey,
                        @Param("eventType") String eventType, @Param("payloadJson") String payloadJson,
                        @Param("occurredAt") LocalDateTime occurredAt);

    List<EventRow> listAfter(@Param("userId") String userId, @Param("taskId") String taskId,
                             @Param("after") long after);

    long maxSeq(@Param("userId") String userId, @Param("taskId") String taskId);

    EventRow findLatestAnswerEvent(@Param("userId") String userId, @Param("taskId") String taskId);
}
