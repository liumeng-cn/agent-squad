package com.agentsquad.squad.runtime.repository;

import com.agentsquad.squad.runtime.domain.EventRow;
import org.apache.ibatis.annotations.*;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 公开事件 SQL 边界；事件与受理命令在同一业务事务中提交。
 */
@Mapper
public interface TaskEventMapper {

    @Insert("""
            INSERT INTO sq_task_event
                (user_id, task_id, event_seq, producer_key, event_type, payload_json, occurred_at)
            VALUES
                (#{userId}, #{taskId}, 1, #{producerKey}, 'task.accepted', #{payloadJson}, #{occurredAt})
            """)
    int insertAccepted(@Param("userId") String userId, @Param("taskId") String taskId,
                       @Param("producerKey") String producerKey, @Param("payloadJson") String payloadJson,
                       @Param("occurredAt") LocalDateTime occurredAt);

    @Insert("""
            INSERT INTO sq_task_event
                (user_id, task_id, event_seq, producer_key, event_type, payload_json, occurred_at)
            VALUES (#{userId}, #{taskId}, #{sequence}, #{producerKey}, #{eventType},
                    #{payloadJson}, #{occurredAt})
            """)
    int insertProjected(@Param("userId") String userId, @Param("taskId") String taskId,
                        @Param("sequence") long sequence, @Param("producerKey") String producerKey,
                        @Param("eventType") String eventType, @Param("payloadJson") String payloadJson,
                        @Param("occurredAt") LocalDateTime occurredAt);

    @ConstructorArgs({
            @Arg(column = "event_seq", javaType = long.class),
            @Arg(column = "task_id", javaType = String.class),
            @Arg(column = "event_type", javaType = String.class),
            @Arg(column = "payload_json", javaType = String.class),
            @Arg(column = "occurred_at", javaType = LocalDateTime.class)
    })
    @Select("""
            SELECT event_seq, task_id, event_type, payload_json, occurred_at
            FROM sq_task_event
            WHERE user_id = #{userId} AND task_id = #{taskId} AND event_seq > #{after}
            ORDER BY event_seq LIMIT 100
            """)
    List<EventRow> listAfter(@Param("userId") String userId, @Param("taskId") String taskId,
                             @Param("after") long after);

    @Select("""
            SELECT COALESCE(MAX(event_seq), 0) FROM sq_task_event
            WHERE user_id = #{userId} AND task_id = #{taskId}
            """)
    long maxSeq(@Param("userId") String userId, @Param("taskId") String taskId);
}
