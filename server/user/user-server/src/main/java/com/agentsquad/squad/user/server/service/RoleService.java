package com.agentsquad.squad.user.server.service;

import com.agentsquad.squad.common.enums.ApiCode;
import com.agentsquad.squad.common.enums.PermissionCode;
import com.agentsquad.squad.common.enums.PermissionStage;
import com.agentsquad.squad.common.enums.RbacOperation;
import com.agentsquad.squad.common.enums.RbacStatus;
import com.agentsquad.squad.common.exceptions.BusinessException;
import com.agentsquad.squad.user.server.dto.RbacStatusDTO;
import com.agentsquad.squad.user.server.dto.RbacVersionDTO;
import com.agentsquad.squad.user.server.dto.RoleAuthorizationDTO;
import com.agentsquad.squad.user.server.dto.RoleCreateDTO;
import com.agentsquad.squad.user.server.dto.RoleUpdateDTO;
import com.agentsquad.squad.user.server.mapper.RbacMapper;
import com.agentsquad.squad.user.server.mapper.RoleMapper;
import com.agentsquad.squad.user.server.po.RolePO;
import com.agentsquad.squad.user.server.vo.RoleVO;
import jakarta.annotation.Resource;
import org.springframework.context.ApplicationEventPublisher;
import com.agentsquad.squad.user.server.observer.AuthorizationChangedEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;

/**
 * 管理角色维护
 * <p>
 * 保存草稿与应用授权分离；全部变更、快照和操作日志在同一事务提交，失败整体回滚。
 */
@Service
@Slf4j
public class RoleService {
    @Resource
    private RbacMapper mapper;

    @Resource
    private ApplicationEventPublisher events;

    @Resource
    private RoleMapper roles;

    @Resource
    private PermissionService access;

    @Resource
    private ManagementQueryService queries;

    @Resource
    private MenuAuthorizationService authorization;

    /**
     * 创建角色草稿
     * <p>
     * 唯一编码由数据库约束裁决；失败不产生权限关联。
     */
    @Transactional
    public RoleVO create(RoleCreateDTO request) {
        access.beginAuthorizationChange(PermissionCode.ROLE_CREATE);
        List<String> codes = List.of();
        RolePO role = new RolePO();
        role.setRoleId(UUID.randomUUID().toString().replace("-", ""));
        role.setCode(request.getCode());
        role.setName(request.getName().strip());
        role.setDescription(request.getDescription().strip());
        role.setStatus(RbacStatus.DISABLED);
        role.setBuiltIn(false);
        role.setVersion(0L);
        role.setCreatorId(access.actor());
        role.setLastUpdaterId(access.actor());
        try {
            roles.insert(role);
        } catch (DuplicateKeyException exception) {
            throw new BusinessException(ApiCode.CONFLICT, "角色编码已存在");
        }
        replacePermissions(role.getRoleId(), PermissionStage.DRAFT, codes);
        writeLog(RbacOperation.ROLE_CREATE, role.getRoleId(), codes.toString());
        return queries.role(role.getRoleId());
    }

    /**
     * 保存角色草稿
     * <p>
     * 核对编辑版本，只替换 DRAFT，不改变已经生效的权限。
     */
    @Transactional
    public RoleVO update(String roleId, RoleUpdateDTO request) {
        RolePO role = requireEditable(roleId, request.getVersion(), PermissionCode.ROLE_UPDATE);
        role.setName(request.getName().strip());
        role.setDescription(request.getDescription().strip());
        if (mapper.updateRoleDraft(role, access.actor()) != 1) {
            throw new BusinessException(ApiCode.CONFLICT, "角色已变更，请刷新");
        }
        writeLog(RbacOperation.ROLE_EDIT, roleId, "修改角色资料");
        return queries.role(roleId);
    }

    /**
     * 应用角色权限
     * <p>
     * 同事务切换权限快照并启用角色，管理请求在提交后读取新的 APPLIED 权限。
     */
    @Transactional
    public RoleVO apply(String roleId, RbacVersionDTO request) {
        requireEditable(roleId, request.getVersion(), PermissionCode.ROLE_APPLY);
        List<String> codes = mapper.rolePermissions(roleId, PermissionStage.DRAFT);
        authorization.validate(menusFor(roleId, PermissionStage.DRAFT), codes);
        replacePermissions(roleId, PermissionStage.APPLIED, codes);
        authorization.copyApplied(roleId);
        mapper.applyRole(roleId, access.actor());
        writeLog(RbacOperation.ROLE_APPLY, roleId, codes.toString());
        return queries.role(roleId);
    }

    /**
     * 切换角色状态
     */
    @Transactional
    public RoleVO status(String roleId, RbacStatusDTO request) {
        RolePO role = requireEditable(roleId, request.getVersion(), request.getStatus() == RbacStatus.ACTIVE ? PermissionCode.ROLE_ENABLE : PermissionCode.ROLE_DISABLE);
        if (request.getStatus() == RbacStatus.ACTIVE && role.getAppliedVersion() == null) {
            throw new BusinessException(ApiCode.BAD_REQUEST, "角色尚未应用，请先应用权限");
        }
        if (request.getStatus() == RbacStatus.ACTIVE) {
            authorization.validate(menusFor(roleId, PermissionStage.APPLIED), mapper.rolePermissions(roleId, PermissionStage.APPLIED));
        }
        mapper.updateRoleStatus(roleId, request.getStatus(), access.actor());
        writeLog(RbacOperation.ROLE_STATUS, roleId, request.getStatus().name());
        return queries.role(roleId);
    }

    private RolePO requireEditable(String roleId, Long version, PermissionCode permission) {
        access.beginAuthorizationChange(permission);
        RolePO role = mapper.findRole(roleId);
        if (role == null) {
            throw new BusinessException(ApiCode.NOT_FOUND, "角色不存在");
        }
        if (Boolean.TRUE.equals(role.getBuiltIn())) {
            throw new BusinessException(ApiCode.FORBIDDEN, "内置平台管理员角色不可修改");
        }
        if (!role.getVersion().equals(version)) {
            throw new BusinessException(ApiCode.CONFLICT, "角色已变更，请刷新后重新确认");
        }
        return role;
    }

    /**
     * 保存授权草稿
     *
     * 菜单和功能同事务替换，基本信息不受影响，版本冲突与异常均回滚。
     */
    @Transactional
    public RoleVO authorization(String roleId, RoleAuthorizationDTO request) {
        requireEditable(roleId, request.getVersion(), PermissionCode.ROLE_UPDATE);
        List<String> menuIds = authorization.validate(request.getMenuIds(), request.getPermissionCodes());
        authorization.replaceDraft(roleId, menuIds, request.getPermissionCodes(), request.getVersion());
        writeLog(RbacOperation.ROLE_EDIT, roleId, "修改菜单和功能授权");
        return queries.role(roleId);
    }

    private List<String> menusFor(String roleId, PermissionStage stage) {
        return authorization.roleMenus(roleId, stage);
    }

    private void replacePermissions(String roleId, PermissionStage stage, List<String> codes) {
        mapper.deleteRolePermissions(roleId, stage);
        if (!codes.isEmpty()) {
            mapper.insertRolePermissions(roleId, codes, stage, access.actor());
        }
    }

    /**
     * 记录授权操作日志，与业务变更同事务提交
     */
    private void writeLog(RbacOperation operation, String roleId, String summary) {
        mapper.insertOperationLog(operation, roleId, summary, access.actor());
        events.publishEvent(new AuthorizationChangedEvent());
        log.info("管理角色变更，operation={}，roleId={}", operation, roleId);
    }

}
