package com.agentsquad.squad.v2.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 对话改名请求
 * <p>
 * 按读取时的标题版本条件更新，版本不一致说明他人已修改，返回业务冲突。
 */
@Data
public class ConvRenameDTO {
    /**
     * 单行对话标题，保存前去除首尾空白
     */
    @NotBlank
    @Size(max = 120)
    private String title;

    /**
     * 读取到的标题版本
     */
    @NotNull
    @PositiveOrZero
    private Long version;
}
