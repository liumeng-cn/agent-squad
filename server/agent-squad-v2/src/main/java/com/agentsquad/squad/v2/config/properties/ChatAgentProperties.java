package com.agentsquad.squad.v2.config.properties;

import lombok.Data;
import com.agentsquad.squad.v2.enums.SparkThinkingMode;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 聊天 Agent 配置
 * <p>
 * 由聊天应用维护模型与工作区配置，持久化沿用框架默认实现。
 */
@Data
@ConfigurationProperties("agentsquad.v2.agent")
public class ChatAgentProperties {
    /**
     * 是否启用模型连接。
     */
    private boolean enabled;

    /**
     * 模型访问密钥。
     */
    private String apiKey = "";

    /**
     * OpenAI 兼容服务地址。
     */
    private String baseUrl = "https://api.deepseek.com/v1";

    /**
     * 模型名称。
     */
    private String modelName = "deepseek-chat";

    /** 星火思考模式；未配置时不额外传入讯飞专用参数。 */
    private SparkThinkingMode sparkThinkingMode;

    /**
     * Agent 定义标识。
     */
    private String agentId = "squad-master";

    /**
     * 页面展示名称。
     */
    private String agentName = "Squad";

    /**
     * 系统提示词。
     */
    private String systemPrompt = "你是 Squad，请直接完成用户请求。";

    /**
     * 框架工作区路径。
     */
    private String workspace = "./.agentscope/v2";
}
