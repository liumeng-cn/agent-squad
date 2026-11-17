package com.agentsquad.squad.user.api.dto;

import lombok.Data;

/**
 * 跨服务账号查询入参
 * <p>
 * 所属user/user-api模块，按明确职责维护用户身份与数据边界。
 */
@Data
public class AccountQueryDTO {
    /**
     * 用于账号查询的手机号
     */
    private String phone;
}
