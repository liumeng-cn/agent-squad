package com.agentsquad.squad.user.server.mapper;

import com.agentsquad.squad.user.server.po.UserPO;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 用户数据库访问
 * <p>
 * 所属user/user-server模块，按明确职责维护用户身份与数据边界。
 */
@Mapper
public interface UserMapper extends BaseMapper<UserPO> {
    Long nextUserId();

    int updateDisplayName(@Param("userId") Long userId, @Param("displayName") String displayName,
                          @Param("version") Long version);
}
