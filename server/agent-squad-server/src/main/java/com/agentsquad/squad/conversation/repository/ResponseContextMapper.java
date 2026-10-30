package com.agentsquad.squad.conversation.repository;

import com.agentsquad.squad.conversation.domain.ResponseContextRow;
import com.agentsquad.squad.conversation.domain.ResponseHistoryRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 回复消息前缀的持久边界。
 * <p>
 * 所有查询限制用户、会话和来源序号，快照只允许首次写入。
 */
@Mapper
public interface ResponseContextMapper {

    ResponseContextRow findSource(@Param("userId") String userId, @Param("bindingId") String bindingId);

    long characterCount(@Param("userId") String userId, @Param("conversationId") String conversationId,
                        @Param("through") long through);

    List<ResponseHistoryRow> prefix(@Param("userId") String userId,
                                    @Param("conversationId") String conversationId, @Param("through") long through);

    List<String> artifactIds(@Param("userId") String userId, @Param("messageId") String messageId);

    int freeze(@Param("userId") String userId, @Param("executionId") String executionId,
               @Param("json") String json, @Param("hash") String hash);
}
