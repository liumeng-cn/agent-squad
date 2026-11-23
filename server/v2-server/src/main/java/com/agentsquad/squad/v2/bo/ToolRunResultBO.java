package com.agentsquad.squad.v2.bo;

import lombok.Data;

/**
 * 工具调用结果
 * <p>
 * 试运行一次工具的结果；请求摘要与输出中的凭据已遮蔽。
 */
@Data
public class ToolRunResultBO {
    /**
     * 是否调用成功
     */
    private boolean success;

    /**
     * 耗时毫秒数
     */
    private long durationMs;

    /**
     * 请求摘要
     */
    private String request;

    /**
     * 工具输出或错误信息
     */
    private String output;
}
