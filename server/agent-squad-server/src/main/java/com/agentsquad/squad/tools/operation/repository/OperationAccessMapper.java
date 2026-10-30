package com.agentsquad.squad.tools.operation.repository;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 目标范围内的核对授权查询。
 * <p>
 * 授权由管理配置建立，业务请求不能自授予角色或目标范围。
 */
@Mapper
public interface OperationAccessMapper {
    int countGrant(@Param("userId") String userId, @Param("scopeId") String scopeId);
}
