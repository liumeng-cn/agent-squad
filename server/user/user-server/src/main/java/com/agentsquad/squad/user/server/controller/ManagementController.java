package com.agentsquad.squad.user.server.controller;

import com.agentsquad.squad.common.model.ApiResult;
import com.agentsquad.squad.common.vo.PageVO;
import com.agentsquad.squad.user.server.dto.UserRolesDTO;
import com.agentsquad.squad.user.server.dto.ManagementPageDTO;
import com.agentsquad.squad.user.server.dto.RoleCreateDTO;
import com.agentsquad.squad.user.server.dto.RoleUpdateDTO;
import com.agentsquad.squad.user.server.dto.RbacStatusDTO;
import com.agentsquad.squad.user.server.dto.RbacVersionDTO;
import com.agentsquad.squad.user.server.service.UserRoleService;
import com.agentsquad.squad.user.server.service.ManagementQueryService;
import com.agentsquad.squad.user.server.service.RoleService;
import com.agentsquad.squad.user.server.vo.ManagementUserVO;
import com.agentsquad.squad.user.server.vo.PermissionVO;
import com.agentsquad.squad.user.server.vo.RoleVO;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 用户平台管理接口
 * <p>
 * 只处理用户角色权限的协议入参；事务、授权快照和最后管理员保护由 Service 承担。
 */
@RestController
@RequestMapping("/api/v2/users/management")
public class ManagementController {
    @Resource
    private ManagementQueryService queries;

    @Resource
    private RoleService roles;

    @Resource
    private UserRoleService members;

    /**
     * 分页查询用户
     */
    @PostMapping("/users/page")
    public ApiResult<PageVO<ManagementUserVO>> users(@Valid @RequestBody ManagementPageDTO query) {
        return ApiResult.success(queries.members(query));
    }

    /**
     * 查找已有账号
     */
    @GetMapping("/users/by-phone")
    public ApiResult<ManagementUserVO> byPhone(@RequestParam String phone) {
        return ApiResult.success(queries.byPhone(phone));
    }

    /**
     * 应用用户角色
     */
    @PutMapping("/users/{userId}/roles")
    public ApiResult<ManagementUserVO> memberRoles(@PathVariable String userId, @Valid @RequestBody UserRolesDTO request) {
        return ApiResult.success(members.roles(userId, request));
    }

    /**
     * 切换管理状态
     */
    @PatchMapping("/users/{userId}/status")
    public ApiResult<ManagementUserVO> memberStatus(@PathVariable String userId, @Valid @RequestBody RbacStatusDTO request) {
        return ApiResult.success(members.status(userId, request));
    }

    /**
     * 分页查询角色
     */
    @PostMapping("/roles/page")
    public ApiResult<PageVO<RoleVO>> roles(@Valid @RequestBody ManagementPageDTO query) {
        return ApiResult.success(queries.roles(query));
    }

    /**
     * 查看角色详情
     */
    @GetMapping("/roles/{roleId}")
    public ApiResult<RoleVO> role(@PathVariable String roleId) {
        return ApiResult.success(queries.role(roleId));
    }

    /**
     * 新增管理角色
     */
    @PostMapping("/roles")
    public ApiResult<RoleVO> createRole(@Valid @RequestBody RoleCreateDTO request) {
        return ApiResult.success(roles.create(request));
    }

    /**
     * 保存角色草稿
     */
    @PutMapping("/roles/{roleId}")
    public ApiResult<RoleVO> updateRole(@PathVariable String roleId, @Valid @RequestBody RoleUpdateDTO request) {
        return ApiResult.success(roles.update(roleId, request));
    }

    /**
     * 应用角色权限
     */
    @PostMapping("/roles/{roleId}/apply")
    public ApiResult<RoleVO> applyRole(@PathVariable String roleId, @Valid @RequestBody RbacVersionDTO request) {
        return ApiResult.success(roles.apply(roleId, request));
    }

    /**
     * 切换角色状态
     */
    @PatchMapping("/roles/{roleId}/status")
    public ApiResult<RoleVO> roleStatus(@PathVariable String roleId, @Valid @RequestBody RbacStatusDTO request) {
        return ApiResult.success(roles.status(roleId, request));
    }

    /**
     * 查询可授角色
     */
    @GetMapping("/users/role-options")
    public ApiResult<java.util.List<RoleVO>> roleOptions() {
        return ApiResult.success(queries.roleOptions());
    }

    /**
     * 查询可选权限
     */
    @GetMapping("/roles/permission-options")
    public ApiResult<PageVO<PermissionVO>> permissionOptions() {
        ManagementPageDTO query = new ManagementPageDTO();
        query.setSize(100);
        return ApiResult.success(queries.permissions(query));
    }

    /**
     * 分页查询权限
     */
    @PostMapping("/permissions/page")
    public ApiResult<PageVO<PermissionVO>> permissions(@Valid @RequestBody ManagementPageDTO query) {
        return ApiResult.success(queries.permissions(query));
    }
}
