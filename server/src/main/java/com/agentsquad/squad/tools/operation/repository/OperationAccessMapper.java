package com.agentsquad.squad.tools.operation.repository;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 目标范围内的核对授权查询。
 * <p>
 * 授权由管理配置建立，业务请求不能自授予角色或目标范围。
 */
@Mapper
public interface OperationAccessMapper {
    @Select("""
            SELECT COUNT(*) FROM sq_operation_reconciler
            WHERE user_id = #{userId} AND task_id = #{taskId} AND del_flag = '0'
            """)
    int countGrant(@Param("userId") String userId, @Param("taskId") String taskId);
}
