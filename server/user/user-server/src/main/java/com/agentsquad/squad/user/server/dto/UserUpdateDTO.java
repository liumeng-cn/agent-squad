package com.agentsquad.squad.user.server.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 用户昵称编辑请求
 * <p>
 * 所属user/user-server模块，按明确职责维护用户身份与数据边界。
 */
@Data
public class UserUpdateDTO {
    /**
     * 昵称，空字符串表示清空
     */
    @NotNull
    @Size(max = 100)
    private String displayName;
    /**
     * 编辑时读取的版本
     */
    @NotNull
    @PositiveOrZero
    private Long version;
}
