package com.agentsquad.squad.v2.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 会话标题编辑请求
 * <p>
 * 标题更新使用版本避免并发覆盖
 */
@Data
public class SessionRenameDTO {
    /**
     * 单行会话名称
     */
    @NotBlank
    @Size(max = 120)
    private String title;
    /**
     * 当前读取的元数据版本
     */
    @NotNull
    @PositiveOrZero
    private Long version;
}
