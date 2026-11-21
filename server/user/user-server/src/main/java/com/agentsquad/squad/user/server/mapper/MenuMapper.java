package com.agentsquad.squad.user.server.mapper;

import com.agentsquad.squad.user.server.bo.MenuBO;
import com.agentsquad.squad.user.server.bo.MenuFunctionBO;
import com.agentsquad.squad.common.enums.PermissionStage;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 菜单授权数据访问
 * <p>
 * 查询目录与角色快照，批量替换由角色事务统一控制。
 */
public interface MenuMapper {
    List<MenuBO> catalog();

    List<MenuFunctionBO> functions();

    List<String> roleMenus(@Param("roleId") String roleId, @Param("stage") PermissionStage stage);

    List<String> effectiveMenus(@Param("userId") Long userId);

    int deleteRoleMenus(@Param("roleId") String roleId, @Param("stage") PermissionStage stage);

    int insertRoleMenus(@Param("roleId") String roleId, @Param("menuIds") List<String> menuIds, @Param("stage") PermissionStage stage, @Param("actor") Long actor);

    int touchRole(@Param("roleId") String roleId, @Param("version") Long version, @Param("actor") Long actor);
}
