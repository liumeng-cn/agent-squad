package com.agentsquad.squad.gateway.admin;

import com.agentsquad.squad.common.enums.identity.AccountRole;
import com.agentsquad.squad.common.enums.identity.AccountStatus;
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

    UserAccountRow findUserById(@Param("userId") String userId);

    UserAccountRow findUserForUpdate(@Param("userId") String userId);

    List<UserAccountRow> listUsers(@Param("cursor") String cursor, @Param("limit") int limit);

    String findAnyUserIdForUpdate();

    int acquireBootstrapLock();

    int releaseBootstrapLock();

    List<String> lockActiveAdmins();

    int insertUser(@Param("userId") String userId, @Param("username") String username,
                   @Param("displayName") String displayName, @Param("passwordHash") String passwordHash,
                   @Param("role") AccountRole role, @Param("mustChangePassword") boolean mustChangePassword,
                   @Param("actorId") String actorId, @Param("actorName") String actorName);

    int updateProfile(@Param("userId") String userId, @Param("displayName") String displayName,
                      @Param("role") AccountRole role, @Param("status") AccountStatus status,
                      @Param("expectedVersion") long expectedVersion, @Param("authBump") long authBump,
                      @Param("actorId") String actorId, @Param("actorName") String actorName);

    int updatePassword(@Param("userId") String userId, @Param("passwordHash") String passwordHash,
                       @Param("mustChangePassword") boolean mustChangePassword,
                       @Param("actorId") String actorId, @Param("actorName") String actorName);
}
