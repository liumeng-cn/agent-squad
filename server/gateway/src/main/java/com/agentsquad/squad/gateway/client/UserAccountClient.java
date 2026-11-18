package com.agentsquad.squad.gateway.client;

import com.agentsquad.squad.common.enums.ApiCode;
import com.agentsquad.squad.common.exceptions.BusinessException;
import com.agentsquad.squad.common.model.ApiResult;
import com.agentsquad.squad.user.api.dto.AccountQueryDTO;
import com.agentsquad.squad.user.api.vo.UserAccountVO;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * 用户事实远程查询适配
 * <p>
 * 所属网关认证模块，登录身份及请求准入统一在网关裁决。
 */
@Component
public class UserAccountClient {
    @Resource
    private WebClient.Builder webClientBuilder;

    @Value("${services.user-server.url:http://127.0.0.1:18083}")
    private String userServerUrl;

    /**
     * 查询手机账号
     */
    public Mono<UserAccountVO> byPhone(String phone) {
        AccountQueryDTO request = new AccountQueryDTO();
        request.setPhone(phone);
        return webClientBuilder.build().post()
                .uri(userServerUrl + "/internal/users/account-query")
                .bodyValue(request)
                .retrieve()
                .bodyToMono(accountResponseType())
                .map(this::unwrap);
    }

    /**
     * 查询认证账号
     */
    public Mono<UserAccountVO> byId(String userId) {
        return webClientBuilder.build().get()
                .uri(userServerUrl + "/internal/users/{userId}/auth", userId)
                .retrieve()
                .bodyToMono(accountResponseType())
                .map(this::unwrap);
    }

    private ParameterizedTypeReference<ApiResult<UserAccountVO>> accountResponseType() {
        return ParameterizedTypeReference.forType(org.springframework.core.ResolvableType
                .forClassWithGenerics(ApiResult.class, UserAccountVO.class).getType());
    }

    private UserAccountVO unwrap(ApiResult<UserAccountVO> response) {
        if (response == null) {
            throw new IllegalStateException("用户服务响应为空");
        }
        if (response.getCode() != 0) {
            throw new BusinessException(ApiCode.fromCode(response.getCode()), response.getMsg());
        }
        return response.getData();
    }
}
