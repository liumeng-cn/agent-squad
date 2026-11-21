package com.agentsquad.squad.user.server.service;

import com.agentsquad.squad.common.enums.ApiCode;
import com.agentsquad.squad.common.enums.ManagementUserStatus;
import com.agentsquad.squad.common.enums.PermissionCode;
import com.agentsquad.squad.common.enums.PermissionStage;
import com.agentsquad.squad.common.enums.RbacOperation;
import com.agentsquad.squad.common.enums.RbacStatus;
import com.agentsquad.squad.common.exceptions.BusinessException;
import com.agentsquad.squad.user.server.bo.ManagementUserBO;
import com.agentsquad.squad.user.server.dto.RbacStatusDTO;
import com.agentsquad.squad.user.server.dto.UserRolesDTO;
import com.agentsquad.squad.user.server.mapper.RbacMapper;
import com.agentsquad.squad.user.server.po.RolePO;
import com.agentsquad.squad.user.server.vo.ManagementUserVO;
import jakarta.annotation.Resource;
import org.springframework.context.ApplicationEventPublisher;
import com.agentsquad.squad.user.server.observer.AuthorizationChangedEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;

/**
 * 平台成员授权
 * <p>
 * 只修改管理角色关联与授权版本，不修改普通账号状态；最后管理员检查与关系变更同事务。
 */
@Service
@Slf4j
public class UserRoleService {
    @Resource
    private RbacMapper mapper;

    @Resource
    private ApplicationEventPublisher events;

    @Resource
    private PermissionService access;

    @Resource
    private ManagementQueryService queries;

    @Resource
    private MenuAuthorizationService authorization;

    /**
     * 应用成员角色
     * <p>
     * 全局授权版本校验防止并发撤销最后管理员，删除与新增关系失败时整体恢复旧授权。
     */
    @Transactional
    public ManagementUserVO roles(String userId, UserRolesDTO request) {
        Long id = access.userId(userId);
        ManagementUserBO member = findMember(id, request.getVersion(), PermissionCode.USER_ASSIGN_ROLE);
        List<String> roleIds = validatedRoles(request.getRoleIds());
        RbacStatus status = member.getStatus() == ManagementUserStatus.DISABLED ? RbacStatus.DISABLED : RbacStatus.ACTIVE;
        mapper.deleteMemberRoles(id);
        if (!roleIds.isEmpty()) {
            mapper.insertMemberRoles(id, roleIds, status, access.actor());
        }
        finishChange(id, request.getVersion(), RbacOperation.MEMBER_ROLES, roleIds.toString());
        return queries.member(id);
    }

    /**
     * 切换管理状态
     */
    @Transactional
    public ManagementUserVO status(String userId, RbacStatusDTO request) {
        Long id = access.userId(userId);
        findMember(id, request.getVersion(), request.getStatus() == RbacStatus.ACTIVE ? PermissionCode.USER_ENABLE : PermissionCode.USER_DISABLE);
        List<String> roleIds = mapper.memberRoles(id);
        if (roleIds.isEmpty()) {
            throw new BusinessException(ApiCode.BAD_REQUEST, "请先为用户分配管理角色");
        }
        if (request.getStatus() == RbacStatus.ACTIVE) {
            validatedRoles(roleIds);
        }
        mapper.updateMemberStatus(id, request.getStatus(), access.actor());
        finishChange(id, request.getVersion(), RbacOperation.MEMBER_STATUS, request.getStatus().name());
        return queries.member(id);
    }

    private ManagementUserBO findMember(Long userId, Long version, PermissionCode permission) {
        access.beginAuthorizationChange(permission);
        ManagementUserBO member = mapper.findMember(userId);
        if (member == null) {
            throw new BusinessException(ApiCode.NOT_FOUND, "正常账号不存在");
        }
        if (!member.getVersion().equals(version)) {
            throw new BusinessException(ApiCode.CONFLICT, "管理授权已变更，请刷新后重新确认");
        }
        return member;
    }

    private List<String> validatedRoles(List<String> requested) {
        if (requested == null || requested.stream().anyMatch(Objects::isNull)) {
            throw new BusinessException(ApiCode.BAD_REQUEST, "角色编号无效");
        }
        List<String> roleIds = new ArrayList<>(new HashSet<>(requested));
        roleIds.sort(String::compareTo);
        for (String roleId : roleIds) {
            RolePO role = mapper.findRole(roleId);
            if (role == null || role.getAppliedVersion() == null) {
                throw new BusinessException(ApiCode.BAD_REQUEST, "所选角色不存在或尚未应用");
            }
            List<String> menus = authorization.roleMenus(roleId, PermissionStage.APPLIED);
            List<String> permissions = mapper.rolePermissions(roleId, PermissionStage.APPLIED);
            authorization.validate(menus, permissions);
            if (!access.current().getPermissionCodes().containsAll(permissions)) {
                throw new BusinessException(ApiCode.FORBIDDEN, "不能授予高于自己权限范围的角色");
            }
        }
        return roleIds;
    }

    private void finishChange(Long userId, Long version, RbacOperation operation, String summary) {
        if (mapper.activeAdministrators() < 1) {
            throw new BusinessException(ApiCode.FORBIDDEN, "至少保留一名启用的平台管理员");
        }
        if (mapper.bumpMemberVersion(userId, version, access.actor()) != 1) {
            throw new BusinessException(ApiCode.CONFLICT, "管理授权已变更，请刷新");
        }
        mapper.insertOperationLog(operation, String.valueOf(userId), summary, access.actor());
        events.publishEvent(new AuthorizationChangedEvent());
        log.info("平台成员授权变更，operation={}，userId={}", operation, userId);
    }
}
