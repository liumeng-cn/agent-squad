package com.agentsquad.squad.v2.dto;

import lombok.Data;

/**
 * 对话归档请求
 * <p>
 * 设置当前用户对话的归档状态。
 */
@Data
public class ConvArchiveDTO {
    /**
     * 是否归档
     */
    private boolean archived;
}
