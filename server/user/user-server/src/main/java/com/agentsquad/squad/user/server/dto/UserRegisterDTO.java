package com.agentsquad.squad.user.server.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

/**
 * 用户注册请求
 * <p>
 * 所属user/user-server模块，按明确职责维护用户身份与数据边界。
 */
@Data
public class UserRegisterDTO {
    /**
     * 中国大陆手机号
     */
    @NotBlank
    @Pattern(regexp = "1[3-9][0-9]{9}")
    private String phone;
    /**
     * 当前测试验证码
     */
    @NotBlank
    private String verificationCode;
}
