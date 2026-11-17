package com.agentsquad.squad.user.server.controller;

import com.agentsquad.squad.common.bo.CurrentUserBO;
import com.agentsquad.squad.common.model.ApiResult;
import com.agentsquad.squad.common.utils.IdentityAssertion;
import com.agentsquad.squad.user.api.dto.AccountQueryDTO;
import com.agentsquad.squad.user.api.vo.UserAccountVO;
import com.agentsquad.squad.user.server.dto.UserRegisterDTO;
import com.agentsquad.squad.user.server.dto.UserUpdateDTO;
import com.agentsquad.squad.user.server.service.UserService;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

/**
 * 用户HTTP接口
 * <p>
 * 所属user/user-server模块，按明确职责维护用户身份与数据边界。
 */
@RestController
public class UserController {
    @Resource
    private UserService userService;

    @PostMapping("/api/v2/users/register")
    public ApiResult<UserAccountVO> register(@Valid @RequestBody UserRegisterDTO request) {
        return ApiResult.success(userService.register(request));
    }

    @PostMapping("/internal/users/account-query")
    public ApiResult<UserAccountVO> query(@RequestBody AccountQueryDTO request) {
        return ApiResult.success(userService.byPhone(request.getPhone()));
    }

    @GetMapping("/internal/users/{userId}/auth")
    public ApiResult<UserAccountVO> auth(@PathVariable String userId) {
        return ApiResult.success(userService.byId(userId));
    }

    @GetMapping("/api/v2/users/me")
    public ApiResult<UserAccountVO> me(HttpServletRequest http) {
        return ApiResult.success(userService.byId(current(http).getUserId()));
    }

    @PatchMapping("/api/v2/users/me")
    public ApiResult<UserAccountVO> update(@Valid @RequestBody UserUpdateDTO request, HttpServletRequest http) {
        return ApiResult.success(userService.update(current(http).getUserId(), request));
    }

    private CurrentUserBO current(HttpServletRequest http) {
        return (CurrentUserBO) http.getAttribute(IdentityAssertion.ATTRIBUTE);
    }
}
