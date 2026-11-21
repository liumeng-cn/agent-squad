package com.agentsquad.squad.user.server.service;

import com.agentsquad.squad.common.enums.ApiCode;
import com.agentsquad.squad.common.enums.MenuType;
import com.agentsquad.squad.common.enums.PermissionStage;
import com.agentsquad.squad.common.enums.RbacStatus;
import com.agentsquad.squad.common.exceptions.BusinessException;
import com.agentsquad.squad.common.utils.CurrentUserContext;
import com.agentsquad.squad.user.api.vo.MenuFunctionVO;
import com.agentsquad.squad.user.api.vo.MenuVO;
import com.agentsquad.squad.user.server.bo.MenuBO;
import com.agentsquad.squad.user.server.bo.MenuFunctionBO;
import com.agentsquad.squad.user.server.converter.RbacConverter;
import com.agentsquad.squad.user.server.mapper.MenuMapper;
import com.agentsquad.squad.user.server.mapper.RbacMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 菜单级联授权
 * <p>
 * 构建目录树并校验菜单闭包、功能归属及可授予范围；快照写入加入角色调用方事务。
 */
@Service
public class MenuAuthorizationService {
    @Resource
    private MenuMapper menus;

    @Resource
    private RbacMapper rbac;

    @Resource
    private RbacConverter converter;

    /**
     * 查询可授权树
     */
    public List<MenuVO> catalogTree() {
        Long actor = Long.valueOf(CurrentUserContext.getUserId());
        return tree(menus.catalog(), menus.functions(), new HashSet<>(menus.effectiveMenus(actor)),
                new HashSet<>(rbac.effectivePermissions(actor)), false);
    }

    /**
     * 查询生效菜单
     */
    public List<MenuVO> effectiveTree(Long userId) {
        return tree(menus.catalog(), List.of(), new HashSet<>(menus.effectiveMenus(userId)), Set.of(), true);
    }

    public boolean hasPage(List<MenuVO> nodes) {
        for (MenuVO node : nodes) {
            if (node.getType() == MenuType.PAGE || hasPage(node.getChildren())) {
                return true;
            }
        }
        return false;
    }

    private List<MenuVO> tree(List<MenuBO> catalog, List<MenuFunctionBO> functions, Set<String> allowedMenus,
                              Set<String> allowedFunctions, boolean effectiveOnly) {
        Map<String, MenuVO> index = new LinkedHashMap<>();
        for (MenuBO menu : catalog) {
            if (effectiveOnly && !allowedMenus.contains(menu.getMenuId())) {
                continue;
            }
            MenuVO node = converter.menu(menu);
            node.setSelectable(allowedMenus.contains(menu.getMenuId()) && menu.getStatus() == RbacStatus.ACTIVE);
            node.setDisabledReason(node.isSelectable() ? null : "当前账号无权分配或菜单已禁用");
            index.put(node.getMenuId(), node);
        }
        for (MenuFunctionBO function : functions) {
            MenuVO owner = index.get(function.getMenuId());
            if (owner == null) {
                continue;
            }
            MenuFunctionVO node = converter.function(function);
            node.setSelectable(owner.isSelectable() && function.getStatus() == RbacStatus.ACTIVE && allowedFunctions.contains(function.getCode()));
            node.setDisabledReason(node.isSelectable() ? null : "当前账号无权分配或功能已禁用");
            owner.getFunctions().add(node);
        }
        List<MenuVO> roots = new ArrayList<>();
        for (MenuVO menu : index.values()) {
            if (menu.getParentMenuId() == null) {
                roots.add(menu);
            } else {
                MenuVO parent = index.get(menu.getParentMenuId());
                if (parent != null) {
                    parent.getChildren().add(menu);
                }
            }
        }
        return roots;
    }

    /**
     * 校验授权范围
     * <p>
     * 校验选中功能必须归属选中页面，写操作依赖查询；补齐祖先但不扩张兄弟节点。
     */
    public List<String> validate(List<String> menuIds, List<String> codes) {
        if (menuIds == null || codes == null) {
            reject("菜单与功能不能为空");
        }
        // 读取操作者范围与目录事实，不使用页面提交的可授予标记。
        Long actor = Long.valueOf(CurrentUserContext.getUserId());
        Set<String> allowedMenus = new HashSet<>(menus.effectiveMenus(actor));
        Set<String> allowedCodes = new HashSet<>(rbac.effectivePermissions(actor));

        Map<String, MenuBO> catalog = new HashMap<>();
        for (MenuBO menu : menus.catalog()) {
            catalog.put(menu.getMenuId(), menu);
        }

        Map<String, MenuFunctionBO> functions = new HashMap<>();
        for (MenuFunctionBO function : menus.functions()) {
            functions.put(function.getCode(), function);
        }

        Set<String> selected = new LinkedHashSet<>(menuIds);
        // 先校验功能归属与查询依赖，再补齐菜单祖先，避免扩张兄弟页面授权。
        validateFunctions(codes, selected, functions, allowedCodes);
        addMenuAncestors(selected, catalog, allowedMenus);
        return new ArrayList<>(selected);
    }

    private void validateFunctions(List<String> codes, Set<String> selected, Map<String, MenuFunctionBO> functions,
                                   Set<String> allowedCodes) {
        for (String code : codes) {
            MenuFunctionBO function = functions.get(code);
            if (function == null || function.getStatus() != RbacStatus.ACTIVE) {
                reject("包含无效或已禁用功能");
            }
            if (!allowedCodes.contains(code)) {
                forbidden();
            }
            if (!selected.contains(function.getMenuId())) {
                reject("功能必须关联已选页面");
            }
            String queryCode = code.substring(0, code.lastIndexOf(':') + 1) + "query";
            if (!code.endsWith(":query") && !codes.contains(queryCode)) {
                reject("写操作必须同时授予该页查询功能");
            }
        }
    }

    private void addMenuAncestors(Set<String> selected, Map<String, MenuBO> catalog, Set<String> allowedMenus) {
        for (String id : new ArrayList<>(selected)) {
            Set<String> visited = new HashSet<>();
            String current = id;
            while (current != null) {
                if (!visited.add(current)) {
                    reject("菜单存在循环引用");
                }
                MenuBO menu = catalog.get(current);
                if (menu == null || menu.getStatus() != RbacStatus.ACTIVE) {
                    reject("包含无效或已禁用菜单");
                }
                if (!allowedMenus.contains(current)) {
                    forbidden();
                }
                selected.add(current);
                current = menu.getParentMenuId();
            }
        }
    }

    public List<String> roleMenus(String roleId, PermissionStage stage) {
        return menus.roleMenus(roleId, stage);
    }

    /**
     * 替换授权草稿
     */
    public void replaceDraft(String roleId, List<String> menuIds, List<String> codes, Long version) {
        replaceMenus(roleId, PermissionStage.DRAFT, menuIds);
        rbac.deleteRolePermissions(roleId, PermissionStage.DRAFT);
        Long actor = Long.valueOf(CurrentUserContext.getUserId());
        if (!codes.isEmpty()) {
            List<String> uniqueCodes = new ArrayList<>(new LinkedHashSet<>(codes));
            rbac.insertRolePermissions(roleId, uniqueCodes, PermissionStage.DRAFT, actor);
        }

        if (menus.touchRole(roleId, version, actor) != 1) {
            throw new BusinessException(ApiCode.CONFLICT, "角色已变更，请刷新");
        }
    }

    /**
     * 切换菜单快照
     */
    public void copyApplied(String roleId) {
        List<String> draftMenus = menus.roleMenus(roleId, PermissionStage.DRAFT);
        replaceMenus(roleId, PermissionStage.APPLIED, draftMenus);
    }

    private void replaceMenus(String roleId, PermissionStage stage, List<String> ids) {
        menus.deleteRoleMenus(roleId, stage);
        if (!ids.isEmpty()) {
            Long actor = Long.valueOf(CurrentUserContext.getUserId());
            menus.insertRoleMenus(roleId, ids, stage, actor);
        }
    }

    private void reject(String message) {
        throw new BusinessException(ApiCode.BAD_REQUEST, message);
    }

    private void forbidden() {
        throw new BusinessException(ApiCode.FORBIDDEN, "不能授予自己不具备的菜单或功能");
    }
}
