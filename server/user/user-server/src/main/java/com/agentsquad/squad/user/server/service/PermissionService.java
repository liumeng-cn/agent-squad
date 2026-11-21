package com.agentsquad.squad.user.server.service;

import com.agentsquad.squad.common.enums.ApiCode;
import com.agentsquad.squad.common.enums.PermissionCode;
import com.agentsquad.squad.common.exceptions.BusinessException;
import com.agentsquad.squad.common.utils.CurrentUserContext;
import com.agentsquad.squad.user.api.dto.PermissionCheckDTO;
import com.agentsquad.squad.user.api.vo.ManagementAccessVO;
import com.agentsquad.squad.user.api.vo.MenuVO;
import com.agentsquad.squad.user.server.bo.PermissionRouteBO;
import com.agentsquad.squad.user.server.mapper.RbacMapper;
import jakarta.annotation.Resource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 管理权限校验
 * <p>
 * 用户模块实时读取生效授权，普通用户返回空权限，不将权限写入登录令牌。
 */
@Service
public class PermissionService {
    @Resource
    private RbacMapper mapper;

    @Resource
    private MenuAuthorizationService authorization;

    @Value("${server.servlet.context-path:/user-server}")
    private String contextPath;

    /**
     * 验证当前用户权限
     *
     * 路由规则及授权均由用户模块裁决，未知接口默认拒绝；用户编号由受网络限制的内部调用方提供。
     */
    public boolean checkPermission(PermissionCheckDTO request) {
        String prefix = contextPath + "/api/v2/users/management";
        if (!request.getPath().startsWith(prefix + "/")) {
            return false;
        }
        String resource = request.getPath().substring(prefix.length());
        List<PermissionRouteBO> rules = mapper.permissionRoutes();
        for (PermissionRouteBO rule : rules) {
            if (!rule.getHttpMethod().equals(request.getMethod())) {
                continue;
            }
            if (Pattern.matches(rule.getPathPattern(), resource)) {
                Long userId = Long.valueOf(request.getUserId());
                List<String> permissions = mapper.effectivePermissions(userId);
                return permissions.contains(rule.getPermissionCode());
            }
        }
        return false;
    }

    /**
     * 查询管理准入
     */
    public ManagementAccessVO current() {
        List<String> codes = mapper.effectivePermissions(actor());
        List<MenuVO> menus = authorization.effectiveTree(actor());
        boolean allowed = authorization.hasPage(menus);
        ManagementAccessVO result = new ManagementAccessVO();
        result.setCanAccess(allowed);
        result.setMenus(menus);
        result.setPermissionCodes(allowed ? codes : List.of());
        return result;
    }

    /**
     * 校验管理权限
     */
    public void require(PermissionCode permission) {
        ManagementAccessVO access = current();
        if (!access.isCanAccess() || !access.getPermissionCodes().contains(permission.getCode())) {
            throw new BusinessException(ApiCode.FORBIDDEN, "没有该管理操作权限");
        }
    }

    /**
     * 开始授权变更
     *
     * 在调用方事务内读取授权版本并条件推进；并发修改返回冲突，回滚同时恢复版本。
     */
    public void beginAuthorizationChange(PermissionCode permission) {
        Long version = mapper.authorizationVersion();
        if (version == null) {
            throw new IllegalStateException("管理权限结构尚未初始化");
        }
        require(permission);

        int changed = mapper.advanceAuthorizationVersion(version, actor());
        if (changed != 1) {
            throw new BusinessException(ApiCode.CONFLICT, "授权已被其他操作修改，请刷新后重试");
        }
    }

    public Long actor() {
        return Long.valueOf(CurrentUserContext.getUserId());
    }

    public Long userId(String value) {
        if (value == null || !value.matches("[1-9][0-9]{0,17}")) {
            throw new BusinessException(ApiCode.BAD_REQUEST, "用户编号无效");
        }
        return Long.valueOf(value);
    }
}
