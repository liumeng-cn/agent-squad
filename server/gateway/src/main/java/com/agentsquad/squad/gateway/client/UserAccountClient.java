package com.agentsquad.squad.gateway.client;

import com.agentsquad.squad.common.enums.ApiCode;
import com.agentsquad.squad.common.exceptions.BusinessException;
import com.agentsquad.squad.common.model.ApiResult;
import com.agentsquad.squad.user.api.client.UserClient;
import com.agentsquad.squad.user.api.dto.AccountQueryDTO;
import com.agentsquad.squad.user.api.vo.UserAccountVO;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * 用户事实远程查询适配
 * <p>
 * 所属网关认证模块，登录身份及请求准入统一在网关裁决。
 */
@Component
public class UserAccountClient {
    @Resource
    private org.springframework.beans.factory.ObjectProvider<UserClient> client;

    public Mono<UserAccountVO> byPhone(String phone) {
        AccountQueryDTO request = new AccountQueryDTO();
        request.setPhone(phone);
        return Mono.fromCallable(() -> unwrap(client.getObject().query(request)))
                .subscribeOn(Schedulers.boundedElastic());
    }

    public Mono<UserAccountVO> byId(String userId) {
        return Mono.fromCallable(() -> unwrap(client.getObject().auth(userId)))
                .subscribeOn(Schedulers.boundedElastic());
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
