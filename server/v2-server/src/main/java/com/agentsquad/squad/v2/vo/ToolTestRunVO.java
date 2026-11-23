package com.agentsquad.squad.v2.vo;

import lombok.Data;

/**
 * 工具试运行结果
 * <p>
 * 一次试运行的结果；请求摘要与输出中的凭据已遮蔽。
 */
@Data
public class ToolTestRunVO {
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
