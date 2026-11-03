package com.agentsquad.squad.conversation.repository;

import com.agentsquad.squad.conversation.domain.DeliveryRow;
import com.agentsquad.squad.conversation.domain.CommandClaimContext;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 持久命令的领取、固定 Binding 读取与原生提交收据写入。
 */
@Mapper
public interface DeliveryMapper {

    String MESSAGE_ORDER_GUARD = """
              AND (sq_command.action <> 'MESSAGE_SUBMIT' OR NOT EXISTS (
                  SELECT 1
                  FROM sq_response_execution current_reply
                  JOIN sq_execution_scope current_scope
                    ON current_scope.user_id = current_reply.user_id
                   AND current_scope.scope_id = current_reply.scope_id
                  JOIN sq_message current_message
                    ON current_message.user_id = current_reply.user_id
                   AND current_message.message_id = current_reply.source_message_id
                  JOIN sq_response_execution earlier_reply
                    ON earlier_reply.user_id = current_reply.user_id
                   AND earlier_reply.conversation_id = current_reply.conversation_id
                  JOIN sq_execution_scope earlier_scope
                    ON earlier_scope.user_id = earlier_reply.user_id
                   AND earlier_scope.scope_id = earlier_reply.scope_id
                   AND earlier_scope.conversation_generation = current_scope.conversation_generation
                  JOIN sq_message earlier_message
                    ON earlier_message.user_id = earlier_reply.user_id
                   AND earlier_message.message_id = earlier_reply.source_message_id
                  WHERE current_reply.user_id = sq_command.user_id
                    AND current_reply.binding_id = sq_command.binding_id
                    AND COALESCE(earlier_reply.dispatch_sequence, earlier_message.message_seq) < COALESCE(current_reply.dispatch_sequence, current_message.message_seq)
                    AND earlier_reply.del_flag = '0'
                    AND earlier_reply.status IN ('QUEUED', 'RUNNING', 'RECOVERING', 'RECONCILING', 'CANCELLING')
              ))
            """;

    /**
     * 按用户轮次发现到期命令，避免单用户积压占满整个扫描窗口。
     *
     * 返回仅代表候选集合；领取事务仍负责校验权限、消息顺序和租约。
     */
    /** 控制命令独立扫描，避免普通候选窗口被积压占满。 */
    List<String> findDueControlIds(@Param("now") LocalDateTime now);

    List<String> findDueIds(@Param("now") LocalDateTime now);

    int claim(@Param("commandId") String commandId, @Param("token") String token,
              @Param("now") LocalDateTime now, @Param("leaseUntil") LocalDateTime leaseUntil);

    CommandClaimContext claimContext(@Param("commandId") String commandId);

    List<String> earlierBlockingReplies(@Param("userId") String userId, @Param("bindingId") String bindingId);

    DeliveryRow findClaimed(@Param("commandId") String commandId, @Param("token") String token);

    String findSubmitTurnId(@Param("userId") String userId, @Param("taskId") String taskId,
                            @Param("bindingId") String bindingId);

    int saveReceipt(@Param("commandId") String commandId, @Param("token") String token,
                    @Param("turnId") String turnId, @Param("runId") String runId,
                    @Param("nativeStatus") String nativeStatus, @Param("now") LocalDateTime now);

    int retry(@Param("commandId") String commandId, @Param("token") String token,
              @Param("dueAt") LocalDateTime dueAt, @Param("errorCode") String errorCode);

    /** 将永不合法的命令收敛为阻断态，保留错误码供人工核对。 */
    /** 仅阻断未领取且不支持的命令，不扩大SDK执行动作白名单。 */
    int blockUnknownPending(@Param("now") LocalDateTime now);

    int blockInvalid(@Param("commandId") String commandId, @Param("token") String token,
                     @Param("errorCode") String errorCode);

    int blockRejectedResume(@Param("commandId") String commandId, @Param("token") String token,
                            @Param("errorCode") String errorCode);
}
