package com.agentsquad.squad.common.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

/**
 * 通用分页请求
 * <p>
 * 业务列表复用页码和每页条数，默认加载第一页。
 */
@Data
public class PageDTO {
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
}
