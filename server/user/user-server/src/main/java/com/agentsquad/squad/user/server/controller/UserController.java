package com.agentsquad.squad.user.server.controller;

import com.agentsquad.squad.common.model.ApiResult;
import com.agentsquad.squad.common.utils.CurrentUserContext;
import com.agentsquad.squad.user.api.dto.AccountQueryDTO;
import com.agentsquad.squad.user.api.vo.UserAccountVO;
import com.agentsquad.squad.user.server.dto.UserRegisterDTO;
import com.agentsquad.squad.user.server.dto.UserUpdateDTO;
import com.agentsquad.squad.user.server.service.UserService;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * 用户HTTP接口
 * <p>
 * 所属user/user-server模块，按明确职责维护用户身份与数据边界。
 */
@RestController
public class UserController {
    @Resource
    private UserService userService;

    /**
     * 注册用户
     */
    @PostMapping("/api/v2/users/register")
    public ApiResult<UserAccountVO> register(@Valid @RequestBody UserRegisterDTO request) {
        return ApiResult.success(userService.register(request));
    }

    /**
     * 查询用户账号
     */
    @PostMapping("/internal/users/account-query")
    public ApiResult<UserAccountVO> query(@RequestBody AccountQueryDTO request) {
        return ApiResult.success(userService.byPhone(request.getPhone()));
    }

    /**
     * 获取认证信息
     */
    @GetMapping("/internal/users/{userId}/auth")
    public ApiResult<UserAccountVO> auth(@PathVariable String userId) {
        return ApiResult.success(userService.byId(userId));
    }

    /**
     * 获取个人信息
     */
    @GetMapping("/api/v2/users/me")
    public ApiResult<UserAccountVO> me() {
        return ApiResult.success(userService.byId(CurrentUserContext.getUserId()));
    }

    /**
     * 编辑个人信息
     */
    @PatchMapping("/api/v2/users/me")
    public ApiResult<UserAccountVO> update(@Valid @RequestBody UserUpdateDTO request) {
        return ApiResult.success(userService.update(CurrentUserContext.getUserId(), request));
    }

}
