package com.agentsquad.squad.user.server.mapper;

import com.agentsquad.squad.user.server.bo.PermissionRouteBO;

import com.agentsquad.squad.common.enums.RbacOperation;
import com.agentsquad.squad.common.enums.RbacStatus;
import com.agentsquad.squad.common.enums.PermissionStage;
import com.agentsquad.squad.user.server.bo.ManagementUserBO;
import com.agentsquad.squad.user.server.bo.PermissionBO;
import com.agentsquad.squad.user.server.bo.RoleBO;
import com.agentsquad.squad.user.server.dto.ManagementPageDTO;
import com.agentsquad.squad.user.server.po.RolePO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 管理授权数据访问
 * <p>
 * 负责联表分页、权限快照与条件状态迁移；SQL 集中在对应 XML。
 */
@Mapper
public interface RbacMapper {
    List<PermissionRouteBO> permissionRoutes();

    List<RoleBO> roleOptions();

    List<String> effectivePermissions(@Param("userId") Long userId);

    Long authorizationVersion();

    int advanceAuthorizationVersion(@Param("version") Long version, @Param("actor") Long actor);

    RolePO findRole(@Param("roleId") String roleId);

    ManagementUserBO findMember(@Param("userId") Long userId);

    RoleBO roleDetail(@Param("roleId") String roleId);

    List<RoleBO> rolePage(@Param("query") ManagementPageDTO query, @Param("offset") long offset);

    long roleCount(@Param("query") ManagementPageDTO query);

    List<PermissionBO> permissionPage(@Param("query") ManagementPageDTO query, @Param("offset") long offset);

    long permissionCount(@Param("query") ManagementPageDTO query);

    List<ManagementUserBO> memberPage(@Param("query") ManagementPageDTO query, @Param("offset") long offset);

    long memberCount(@Param("query") ManagementPageDTO query);

    ManagementUserBO memberByPhone(@Param("phone") String phone);

    ManagementUserBO memberDetail(@Param("userId") Long userId);

    List<String> memberRoles(@Param("userId") Long userId);

    List<String> rolePermissions(@Param("roleId") String roleId, @Param("stage") PermissionStage stage);

    List<String> knownPermissions();

    int deleteRolePermissions(@Param("roleId") String roleId, @Param("stage") PermissionStage stage);

    int insertRolePermissions(@Param("roleId") String roleId, @Param("codes") List<String> codes, @Param("stage") PermissionStage stage, @Param("actor") Long actor);

    int updateRoleDraft(@Param("role") RolePO role, @Param("actor") Long actor);

    int applyRole(@Param("roleId") String roleId, @Param("actor") Long actor);

    int updateRoleStatus(@Param("roleId") String roleId, @Param("status") RbacStatus status, @Param("actor") Long actor);

    int deleteMemberRoles(@Param("userId") Long userId);

    int insertMemberRoles(@Param("userId") Long userId, @Param("roleIds") List<String> roleIds, @Param("status") RbacStatus status, @Param("actor") Long actor);

    int updateMemberStatus(@Param("userId") Long userId, @Param("status") RbacStatus status, @Param("actor") Long actor);

    int bumpMemberVersion(@Param("userId") Long userId, @Param("version") Long version, @Param("actor") Long actor);

    long activeAdministrators();

    int insertOperationLog(@Param("operation") RbacOperation operation, @Param("targetId") String targetId, @Param("summary") String summary, @Param("actor") Long actor);
}
