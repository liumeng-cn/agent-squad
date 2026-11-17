package com.agentsquad.squad.mapper;

import com.agentsquad.squad.model.bo.ResponseContextBO;
import com.agentsquad.squad.model.bo.ResponseHistoryBO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 回复消息前缀的
 * <p>
 * 所有查询限制用户、会话和来源序号，快照只允许首次写入。
 */
@Mapper
public interface ResponseContextMapper {

    ResponseContextBO findSource(@Param("userId") Long userId, @Param("bindingId") Long bindingId);

    long characterCount(@Param("userId") Long userId, @Param("conversationId") Long conversationId,
                        @Param("through") long through);

    List<ResponseHistoryBO> prefix(@Param("userId") Long userId,
                                   @Param("conversationId") Long conversationId, @Param("through") long through);

    List<Long> artifactIds(@Param("userId") Long userId, @Param("messageId") Long messageId);

    int freeze(@Param("userId") Long userId, @Param("executionId") Long executionId,
               @Param("json") String json, @Param("hash") String hash);
}
