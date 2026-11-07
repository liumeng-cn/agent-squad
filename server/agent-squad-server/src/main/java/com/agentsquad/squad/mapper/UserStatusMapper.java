package com.agentsquad.squad.mapper;

import com.agentsquad.squad.common.enums.identity.AccountStatus;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 业务服务只读账号状态。
 *
 * <p>网关与业务服务共享同一身份库；业务端不写账号，只在受控执行点确认账号仍可执行。</p>
 */
@Mapper
public interface UserStatusMapper {

    AccountStatus findStatus(@Param("userId") String userId);
}
