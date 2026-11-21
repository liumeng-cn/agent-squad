package com.agentsquad.squad.user.api.client;

import com.agentsquad.squad.common.model.ApiResult;
import com.agentsquad.squad.common.utils.IdentityAssertion;
import com.agentsquad.squad.user.api.dto.AccountQueryDTO;
import com.agentsquad.squad.user.api.dto.PermissionCheckDTO;
import com.agentsquad.squad.user.api.vo.ManagementAccessVO;
import com.agentsquad.squad.user.api.vo.UserAccountVO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.web.bind.annotation.*;


/**
 * 用户服务跨服务调用契约
 * <p>
 * 所属user/user-api模块，按明确职责维护用户身份与数据边界。
 */
@FeignClient(name = "user-server", path = "${services.user-server.path:/user-server}", url = "${services.user-server.url:http://127.0.0.1:18083}")
public interface UserClient {
    /**
     * 查询用户账号
     */
    @PostMapping("/internal/users/account-query")
    ApiResult<UserAccountVO> query(@RequestBody AccountQueryDTO request);

    /**
     * 获取用户信息
     */
    @GetMapping("/internal/users/{userId}/auth")
    ApiResult<UserAccountVO> auth(@PathVariable("userId") String userId);

    /**
     * 查询管理准入
     */
    @GetMapping("/api/v2/users/me/management-access")
    ApiResult<ManagementAccessVO> managementAccess(@RequestHeader(IdentityAssertion.HEADER) String assertion);

    /**
     * 验证当前用户权限
     */
    @Cacheable(cacheNames = "permissionChecks", keyGenerator = "permissionCacheKeyGenerator",
            unless = "#result == null || #result.code != 0")
    @PostMapping("/internal/users/permission-check")
    ApiResult<Boolean> checkPermission(@RequestBody PermissionCheckDTO request);

}
