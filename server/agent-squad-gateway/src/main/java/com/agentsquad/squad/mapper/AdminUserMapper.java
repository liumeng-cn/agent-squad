package com.agentsquad.squad.mapper;

import com.agentsquad.squad.enums.AccountRole;
import com.agentsquad.squad.enums.AccountStatus;
import com.agentsquad.squad.po.UserAccountPO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 账号生命周期
 *
 * <p>行锁与 CAS 更新用于保护最后管理员约束和并发修改；SQL 见 mapper/admin/AdminUserMapper.xml。</p>
 */
@Mapper
public interface AdminUserMapper {

    UserAccountPO findUserById(@Param("userId") Long userId);

    UserAccountPO findUserForUpdate(@Param("userId") Long userId);

    List<UserAccountPO> listUsers(@Param("cursor") Long cursor, @Param("limit") int limit);

    Long findAnyUserIdForUpdate();

    int acquireBootstrapLock();

    int releaseBootstrapLock();

    List<Long> lockActiveAdmins();

    int insertUser(@Param("userId") Long userId, @Param("username") String username,
                   @Param("displayName") String displayName, @Param("passwordHash") String passwordHash,
                   @Param("role") AccountRole role, @Param("mustChangePassword") boolean mustChangePassword,
                   @Param("actorId") Long actorId, @Param("actorName") String actorName);

    int updateProfile(@Param("userId") Long userId, @Param("displayName") String displayName,
                      @Param("role") AccountRole role, @Param("status") AccountStatus status,
                      @Param("expectedVersion") long expectedVersion, @Param("authBump") long authBump,
                      @Param("actorId") Long actorId, @Param("actorName") String actorName);

    int updatePassword(@Param("userId") Long userId, @Param("passwordHash") String passwordHash,
                       @Param("mustChangePassword") boolean mustChangePassword,
                       @Param("actorId") Long actorId, @Param("actorName") String actorName);
}
