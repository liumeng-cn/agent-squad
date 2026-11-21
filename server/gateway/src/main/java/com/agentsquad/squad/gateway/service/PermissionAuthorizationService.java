package com.agentsquad.squad.gateway.service;

import com.agentsquad.squad.common.bo.CurrentUserBO;
import com.agentsquad.squad.common.enums.ApiCode;
import com.agentsquad.squad.common.enums.PermissionCode;
import com.agentsquad.squad.common.exceptions.BusinessException;
import com.agentsquad.squad.common.model.ApiResult;
import com.agentsquad.squad.common.utils.IdentityAssertion;
import com.agentsquad.squad.gateway.config.properties.AuthProperties;
import com.agentsquad.squad.user.api.vo.ManagementAccessVO;
import jakarta.annotation.Resource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.ResolvableType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;

/**
 * 管理接口权限验证
 * <p>
 * 网关在登录认证后按请求方法和路径裁决；权限实时查询，未知管理接口默认拒绝，查询失败不放行。
 */
@Service
public class PermissionAuthorizationService {
    private static final String PREFIX = "/api/v2/users/management";
    private static final String ACCESS_PATH = "/api/v2/users/me/management-access";

    @Value("${services.user-server.path:/user-server}")
    private String userServerPath = "/user-server";

    @Value("${services.v2-server.path:/v2-server}")
    private String runtimeServerPath = "/v2-server";

    @Resource
    private WebClient.Builder webClientBuilder;

    @Resource
    private AuthProperties properties;

    @Value("${services.user-server.url:http://127.0.0.1:18083}")
    private String userServerUrl;

    /**
     * 验证请求权限
     * <p>
     * 普通接口沿用登录认证；管理接口必须同时具备平台准入和目标操作权限。
     */
    public Mono<Void> authorize(CurrentUserBO user, String method, String path) {
        if (path.contains("%") || path.contains(";") || path.contains("\\") || path.contains("//")
                || path.contains("/./") || path.contains("/../")) {
            return denied();
        }
        if (!path.startsWith(userServerPath + "/api/") && !path.startsWith(runtimeServerPath + "/api/")) {
            return denied();
        }
        String managementPath = userServerPath + PREFIX;
        if (!path.equals(managementPath) && !path.startsWith(managementPath + "/")) {
            return Mono.empty();
        }
        PermissionCode required = requiredPermission(method, path);
        if (required == null) {
            return denied();
        }
        String accessPath = userServerPath + ACCESS_PATH;
        String assertion = IdentityAssertion.sign(user, "user-server", "GET", accessPath, properties.getIdentityKey());
        return webClientBuilder.build().get().uri(userServerUrl + accessPath)
                .header(IdentityAssertion.HEADER, assertion)
                .retrieve()
                .bodyToMono(permissionResponseType())
                .timeout(Duration.ofSeconds(3))
                .switchIfEmpty(Mono.error(new IllegalStateException("权限查询响应为空")))
                .flatMap(response -> verify(response, required));
    }

    /**
     * 匹配接口权限
     * <p>
     * 特殊选项接口先匹配，资源详情只允许一个路径段，未声明的方法不能借用读取权限。
     */
    public PermissionCode requiredPermission(String method, String path) {
        String prefix = userServerPath + PREFIX;
        String resource = path.startsWith(prefix + "/") ? path.substring(prefix.length()) : "";
        if (method.equals("POST") && resource.equals("/users/page")) {
            return PermissionCode.USER_READ;
        }
        if (method.equals("GET") && (resource.equals("/users/by-phone") || resource.equals("/users/role-options"))) {
            return PermissionCode.USER_MANAGE;
        }
        if (method.equals("PUT") && resource.matches("/users/[1-9][0-9]{0,17}/roles")) {
            return PermissionCode.USER_MANAGE;
        }
        if (method.equals("PATCH") && resource.matches("/users/[1-9][0-9]{0,17}/status")) {
            return PermissionCode.USER_MANAGE;
        }
        if (method.equals("GET") && resource.equals("/roles/permission-options")) {
            return PermissionCode.ROLE_MANAGE;
        }
        if (method.equals("POST") && resource.equals("/roles/page")) {
            return PermissionCode.ROLE_READ;
        }
        if (method.equals("GET") && resource.matches("/roles/[a-zA-Z0-9-]+")) {
            return PermissionCode.ROLE_READ;
        }
        if (method.equals("POST") && (resource.equals("/roles") || resource.matches("/roles/[a-zA-Z0-9-]+/apply"))) {
            return PermissionCode.ROLE_MANAGE;
        }
        if (method.equals("PUT") && resource.matches("/roles/[a-zA-Z0-9-]+")) {
            return PermissionCode.ROLE_MANAGE;
        }
        if (method.equals("PATCH") && resource.matches("/roles/[a-zA-Z0-9-]+/status")) {
            return PermissionCode.ROLE_MANAGE;
        }
        if (method.equals("POST") && resource.equals("/permissions/page")) {
            return PermissionCode.PERMISSION_READ;
        }
        return null;
    }

    private ParameterizedTypeReference<ApiResult<ManagementAccessVO>> permissionResponseType() {
        return ParameterizedTypeReference.forType(ResolvableType
                .forClassWithGenerics(ApiResult.class, ManagementAccessVO.class).getType());
    }

    private Mono<Void> verify(ApiResult<ManagementAccessVO> response, PermissionCode required) {
        ManagementAccessVO access = response.getData();
        if (response.getCode() != 0 || access == null || !access.isCanAccess()
                || access.getPermissionCodes() == null || !access.getPermissionCodes().contains(required.getCode())) {
            return denied();
        }
        return Mono.empty();
    }

    private Mono<Void> denied() {
        return Mono.error(new BusinessException(ApiCode.FORBIDDEN, "没有该管理操作权限"));
    }
}
