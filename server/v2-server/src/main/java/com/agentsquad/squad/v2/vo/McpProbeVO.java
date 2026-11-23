package com.agentsquad.squad.v2.vo;

import lombok.Data;

import java.util.List;

/**
 * MCP 连接测试结果
 * <p>
 * 按未保存的连接配置建立临时连接并列出远程工具，连接随后关闭。
 */
@Data
public class McpProbeVO {
    /**
     * 连接与发现耗时毫秒数
     */
    private long durationMs;

    /**
     * 发现的远程工具
     */
    private List<McpRemoteToolVO> tools;
}
