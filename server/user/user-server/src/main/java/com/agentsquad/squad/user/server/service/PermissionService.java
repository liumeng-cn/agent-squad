package com.agentsquad.squad.user.server.service;

import com.agentsquad.squad.common.enums.PermissionCode;
import com.agentsquad.squad.common.enums.ApiCode;
import com.agentsquad.squad.common.exceptions.BusinessException;
import com.agentsquad.squad.common.utils.CurrentUserContext;
import com.agentsquad.squad.user.server.mapper.RbacMapper;
import com.agentsquad.squad.user.api.vo.ManagementAccessVO;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 管理权限校验
 * <p>
 * 用户模块实时读取生效授权，普通用户返回空权限，不将权限写入登录令牌。
 */
@Service
public class PermissionService {
    @Resource
    private RbacMapper mapper;

    /**
     * 查询管理准入
     */
    public ManagementAccessVO current() {
        List<String> codes = mapper.effectivePermissions(actor());
        boolean allowed = codes.contains(PermissionCode.ACCESS.getCode());
        ManagementAccessVO result = new ManagementAccessVO();
        result.setCanAccess(allowed);
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
     * 串行授权变更
     * <p>
     * 调用方必须位于事务内，锁定全局授权行后重新校验操作者，防止排队期间撤权后仍执行。
     */
    public void lockFor(PermissionCode permission) {
        if (mapper.lockAuthorization() == null) {
            throw new IllegalStateException("管理权限结构尚未初始化");
        }
        require(permission);
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
