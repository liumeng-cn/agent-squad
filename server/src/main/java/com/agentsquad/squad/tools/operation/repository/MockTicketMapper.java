package com.agentsquad.squad.tools.operation.repository;

import org.apache.ibatis.annotations.*;

/**
 * 本地模拟外部工单系统 SQL 边界；只用于 D2 受控写入与故障注入验证。
 */
@Mapper
public interface MockTicketMapper {

    @Insert("""
            INSERT INTO sq_mock_ticket (user_id, external_key, request_hash, title, detail)
            VALUES (#{userId}, #{externalKey}, #{requestHash}, #{title}, #{detail})
            """)
    int insert(@Param("userId") String userId, @Param("externalKey") String externalKey,
               @Param("requestHash") String requestHash, @Param("title") String title,
               @Param("detail") String detail);

    @Select("""
            SELECT COUNT(*) FROM sq_mock_ticket
            WHERE user_id = #{userId} AND external_key = #{externalKey}
            """)
    int countByKey(@Param("userId") String userId, @Param("externalKey") String externalKey);

    @Select("SELECT COUNT(*) FROM sq_mock_ticket WHERE user_id = #{userId}")
    int countByUser(@Param("userId") String userId);

    @Select("""
            SELECT request_hash FROM sq_mock_ticket
            WHERE user_id = #{userId} AND external_key = #{externalKey}
            """)
    String findRequestHash(@Param("userId") String userId, @Param("externalKey") String externalKey);
}
