package com.agentsquad.squad.gateway.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

/**
 * 手机号登录请求
 * <p>
 * 所属网关认证模块，登录身份及请求准入统一在网关裁决。
 */
@Data
public class LoginDTO {
    /**
     * 中国大陆手机号
     */
    @NotBlank
    @Pattern(regexp = "1[3-9][0-9]{9}")
    private String phone;
    /**
     * 测试验证码
     */
    @NotBlank
    private String verificationCode;
}
