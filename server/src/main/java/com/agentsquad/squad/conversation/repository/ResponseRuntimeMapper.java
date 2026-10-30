package com.agentsquad.squad.conversation.repository;

import com.agentsquad.squad.conversation.domain.ResponseRuntimeRow;
import org.apache.ibatis.annotations.Arg;
import org.apache.ibatis.annotations.ConstructorArgs;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 回复生命周期、完成候选及公开消息的持久边界。
 * <p>
 * 调用方先持有会话和回复所有者锁，再维护候选、命令与消息，保证重投幂等。
 */
@Mapper
public interface ResponseRuntimeMapper {

    @ConstructorArgs({
            @Arg(column = "execution_id", javaType = String.class), @Arg(column = "user_id", javaType = String.class),
            @Arg(column = "conversation_id", javaType = String.class), @Arg(column = "source_message_id", javaType = String.class),
            @Arg(column = "scope_id", javaType = String.class), @Arg(column = "binding_id", javaType = String.class),
            @Arg(column = "status", javaType = String.class), @Arg(column = "version", javaType = long.class),
            @Arg(column = "cancelled", javaType = boolean.class), @Arg(column = "completion_proposal_json", javaType = String.class),
            @Arg(column = "proposal_turn_id", javaType = String.class), @Arg(column = "proposal_hash", javaType = String.class),
            @Arg(column = "correction_count", javaType = int.class), @Arg(column = "recovery_attempts", javaType = int.class),
            @Arg(column = "answer_message_id", javaType = String.class)
    })
    @Select("""
            SELECT execution_id,user_id,conversation_id,source_message_id,scope_id,binding_id,status,version,
                   (cancel_requested_at IS NOT NULL) AS cancelled,completion_proposal_json,proposal_turn_id,
                   proposal_hash,correction_count,recovery_attempts,answer_message_id
            FROM sq_response_execution WHERE user_id=#{userId} AND binding_id=#{bindingId} FOR UPDATE
            """)
    ResponseRuntimeRow lockByBinding(@Param("userId") String userId, @Param("bindingId") String bindingId);

    @Select("SELECT binding_id FROM sq_response_execution WHERE user_id=#{userId} AND execution_id=#{executionId} AND del_flag='0'")
    String bindingForExecution(@Param("userId") String userId, @Param("executionId") String executionId);

    @Update("""
            UPDATE sq_response_execution SET status=#{status},version=version+1
            WHERE user_id=#{userId} AND binding_id=#{bindingId}
              AND status NOT IN ('SUCCEEDED','PARTIAL','FAILED','CANCELLED')
            """)
    int status(@Param("userId") String userId, @Param("bindingId") String bindingId, @Param("status") String status);

    @Update("""
            UPDATE sq_response_execution SET completion_proposal_json=#{json},proposal_turn_id=#{turnId},proposal_hash=#{hash}
            WHERE user_id=#{userId} AND binding_id=#{bindingId} AND completion_proposal_json IS NULL
              AND cancel_requested_at IS NULL AND status IN ('QUEUED','RUNNING','RECOVERING')
            """)
    int propose(@Param("userId") String userId, @Param("bindingId") String bindingId,
                @Param("turnId") String turnId, @Param("json") String json, @Param("hash") String hash);

    @Update("""
            UPDATE sq_response_execution SET status=#{status},version=version+1,answer_message_id=#{answerId}
            WHERE user_id=#{userId} AND binding_id=#{bindingId}
              AND status NOT IN ('SUCCEEDED','PARTIAL','FAILED','CANCELLED')
            """)
    int finish(@Param("userId") String userId, @Param("bindingId") String bindingId,
               @Param("status") String status, @Param("answerId") String answerId);

    @Update("""
            UPDATE sq_response_execution SET correction_count=correction_count+1,completion_proposal_json=NULL,
                   proposal_turn_id=NULL,proposal_hash=NULL,status='QUEUED',version=version+1
            WHERE user_id=#{userId} AND binding_id=#{bindingId} AND correction_count=0 AND cancel_requested_at IS NULL
            """)
    int correct(@Param("userId") String userId, @Param("bindingId") String bindingId);

    @Update("""
            UPDATE sq_response_execution SET recovery_attempts=recovery_attempts+1,status='RECOVERING',version=version+1
            WHERE user_id=#{userId} AND binding_id=#{bindingId} AND recovery_attempts<2 AND cancel_requested_at IS NULL
              AND status NOT IN ('SUCCEEDED','PARTIAL','FAILED','CANCELLED')
            """)
    int recovering(@Param("userId") String userId, @Param("bindingId") String bindingId);

    @Update("""
            UPDATE sq_response_execution SET cancel_requested_at=#{now},status='CANCELLING',version=version+1
            WHERE user_id=#{userId} AND binding_id=#{bindingId} AND cancel_requested_at IS NULL
              AND status NOT IN ('SUCCEEDED','PARTIAL','FAILED','CANCELLED')
            """)
    int cancelling(@Param("userId") String userId, @Param("bindingId") String bindingId, @Param("now") LocalDateTime now);

    @Update("""
            UPDATE sq_command SET status='CANCELLED',delivery_token=NULL,delivery_lease_until=NULL
            WHERE user_id=#{userId} AND binding_id=#{bindingId} AND status IN ('PENDING','DELIVERING')
            """)
    int cancelPending(@Param("userId") String userId, @Param("bindingId") String bindingId);

    @Select("""
            SELECT id FROM sq_operation WHERE user_id=#{userId} AND scope_id=#{scopeId}
              AND status IN ('UNKNOWN','RECONCILING','IN_FLIGHT') AND del_flag='0' FOR UPDATE
            """)
    List<Long> uncertainIds(@Param("userId") String userId, @Param("scopeId") String scopeId);

    @Select("""
            SELECT id FROM sq_sandbox_execution WHERE user_id=#{userId} AND scope_id=#{scopeId}
              AND status IN ('IN_FLIGHT','UNKNOWN') AND del_flag='0' FOR UPDATE
            """)
    List<Long> uncertainSandboxIds(@Param("userId") String userId, @Param("scopeId") String scopeId);

    default int uncertain(String userId, String scopeId) {
        return uncertainIds(userId, scopeId).size() + uncertainSandboxIds(userId, scopeId).size();
    }

    @Select("""
            SELECT kind FROM sq_interaction WHERE user_id=#{userId} AND scope_id=#{scopeId}
              AND status='OPEN' AND del_flag='0' FOR UPDATE
            """)
    List<String> openKinds(@Param("userId") String userId, @Param("scopeId") String scopeId);

    default int openInteractions(String userId, String scopeId) {
        return openKinds(userId, scopeId).size();
    }

    @Insert("""
            INSERT INTO sq_message(message_id,user_id,conversation_id,message_seq,role,content,completed,
                 source_message_id,execution_id,occurred_at,creator_id,last_updater_id)
            VALUES(#{messageId},#{userId},#{conversationId},#{sequence},'ASSISTANT',#{text},TRUE,
                 #{sourceMessageId},#{executionId},#{now},#{userId},#{userId})
            """)
    int answer(@Param("messageId") String messageId, @Param("userId") String userId,
               @Param("conversationId") String conversationId, @Param("sequence") long sequence,
               @Param("sourceMessageId") String sourceMessageId, @Param("executionId") String executionId,
               @Param("text") String text, @Param("now") LocalDateTime now);

    @Select("""
            SELECT command_id FROM sq_command WHERE user_id=#{userId} AND binding_id=#{bindingId}
              AND action IN ('MESSAGE_SUBMIT','SUBMIT') AND status IN ('DELIVERED','APPLIED')
              AND native_turn_id IS NOT NULL ORDER BY id DESC LIMIT 1
            """)
    String originalCommand(@Param("userId") String userId, @Param("bindingId") String bindingId);

    @Select("""
            SELECT binding_id FROM sq_response_execution WHERE user_id=#{userId} AND conversation_id=#{conversationId}
              AND status NOT IN ('SUCCEEDED','PARTIAL','FAILED','CANCELLED') ORDER BY id
            """)
    List<String> activeBindings(@Param("userId") String userId, @Param("conversationId") String conversationId);
}
