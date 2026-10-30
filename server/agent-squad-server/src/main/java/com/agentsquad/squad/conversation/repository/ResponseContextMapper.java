package com.agentsquad.squad.conversation.repository;

import com.agentsquad.squad.conversation.domain.ResponseContextRow;
import com.agentsquad.squad.conversation.domain.ResponseHistoryRow;
import org.apache.ibatis.annotations.Arg;
import org.apache.ibatis.annotations.ConstructorArgs;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * 回复消息前缀的持久边界。
 * <p>
 * 所有查询限制用户、会话和来源序号，快照只允许首次写入。
 */
@Mapper
public interface ResponseContextMapper {

    @ConstructorArgs({
            @Arg(column = "execution_id", javaType = String.class),
            @Arg(column = "source_message_id", javaType = String.class),
            @Arg(column = "message_seq", javaType = long.class),
            @Arg(column = "context_json", javaType = String.class),
            @Arg(column = "context_hash", javaType = String.class)
    })
    @Select("""
            SELECT r.execution_id, r.source_message_id, m.message_seq, r.context_json, r.context_hash
            FROM sq_response_execution r JOIN sq_message m
              ON m.user_id = r.user_id AND m.message_id = r.source_message_id
              AND m.conversation_id = r.conversation_id
            WHERE r.user_id = #{userId} AND r.binding_id = #{bindingId} AND r.del_flag = '0'
              AND m.del_flag = '0' AND m.role = 'USER' AND m.completed = TRUE FOR UPDATE
            """)
    ResponseContextRow findSource(@Param("userId") String userId, @Param("bindingId") String bindingId);

    @Select("""
            SELECT COALESCE(SUM(CHAR_LENGTH(content)), 0) FROM sq_message
            WHERE user_id = #{userId} AND conversation_id = #{conversationId}
              AND message_seq <= #{through} AND completed = TRUE AND del_flag = '0'
            """)
    long characterCount(@Param("userId") String userId, @Param("conversationId") String conversationId,
                        @Param("through") long through);

    @ConstructorArgs({
            @Arg(column = "message_id", javaType = String.class),
            @Arg(column = "message_seq", javaType = long.class),
            @Arg(column = "role", javaType = String.class),
            @Arg(column = "content", javaType = String.class),
            @Arg(column = "revision", javaType = long.class)
    })
    @Select("""
            SELECT message_id, message_seq, role, content, revision
            FROM sq_message WHERE user_id = #{userId} AND conversation_id = #{conversationId}
              AND message_seq <= #{through} AND completed = TRUE AND del_flag = '0'
            ORDER BY message_seq FOR UPDATE
            """)
    List<ResponseHistoryRow> prefix(@Param("userId") String userId,
                                    @Param("conversationId") String conversationId, @Param("through") long through);

    @Select("""
            SELECT artifact_id FROM sq_message_artifact
            WHERE user_id = #{userId} AND message_id = #{messageId} AND del_flag = '0' ORDER BY id
            """)
    List<String> artifactIds(@Param("userId") String userId, @Param("messageId") String messageId);

    @Update("""
            UPDATE sq_response_execution SET context_json = #{json}, context_hash = #{hash}
            WHERE user_id = #{userId} AND execution_id = #{executionId} AND context_json IS NULL
            """)
    int freeze(@Param("userId") String userId, @Param("executionId") String executionId,
               @Param("json") String json, @Param("hash") String hash);
}
