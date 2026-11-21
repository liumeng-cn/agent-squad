package com.agentsquad.squad.user.server.controller;

import com.agentsquad.squad.common.model.ApiResult;
import com.agentsquad.squad.user.server.service.PermissionService;
import com.agentsquad.squad.user.api.vo.ManagementAccessVO;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 当前用户管理权限
 * <p>
 * 复用网关可信登录身份，未授权普通用户得到空权限，不影响个人功能。
 */
@RestController
public class ManagementAccessController {
    @Resource
    private PermissionService access;

    /**
     * 查询管理准入
     */
    @GetMapping("/api/v2/users/me/management-access")
    public ApiResult<ManagementAccessVO> current() {
        return ApiResult.success(access.current());
    }
}
