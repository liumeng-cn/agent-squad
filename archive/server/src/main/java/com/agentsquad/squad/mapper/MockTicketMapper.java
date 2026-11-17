package com.agentsquad.squad.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 本地模拟外部工单系统 ；只用于 D2 受控写入与故障注入验证
 */
@Mapper
public interface MockTicketMapper {

    int insert(@Param("userId") Long userId, @Param("externalKey") String externalKey,
               @Param("requestHash") String requestHash, @Param("title") String title,
               @Param("detail") String detail);

    int countByKey(@Param("userId") Long userId, @Param("externalKey") String externalKey);

    int countByUser(@Param("userId") Long userId);

    String findRequestHash(@Param("userId") Long userId, @Param("externalKey") String externalKey);
}
