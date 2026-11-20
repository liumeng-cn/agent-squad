package com.agentsquad.squad.v2.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

/**
 * 对话分页查询
 * <p>
 * 默认查询进行中的对话；归档对话从主列表移除，通过 archived 单独查询。
 */
@Data
public class ConvPageQueryDTO {
    /**
     * 当前页码
     */
    @Min(1)
    private long current = 1;

    /**
     * 每页条数
     */
    @Min(1)
    @Max(100)
    private long size = 30;

    /**
     * 是否查询已归档对话
     */
    private boolean archived;
}
