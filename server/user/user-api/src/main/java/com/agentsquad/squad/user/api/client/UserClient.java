package com.agentsquad.squad.user.api.client;

import com.agentsquad.squad.common.model.ApiResult;
import com.agentsquad.squad.user.api.dto.AccountQueryDTO;
import com.agentsquad.squad.user.api.vo.UserAccountVO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

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
}
