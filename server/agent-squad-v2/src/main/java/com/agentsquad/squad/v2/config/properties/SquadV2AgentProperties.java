package com.agentsquad.squad.v2.config.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** V2 主 Agent 配置，密钥只从运行环境注入。 */
@Data
@ConfigurationProperties(prefix = "agentsquad.v2.agent")
public class SquadV2AgentProperties {
    /** 是否启用真实 AgentScope 模型。 */
    private boolean enabled;
    /** 模型 API Key。 */
    private String apiKey = "";
    /** OpenAI 兼容模型服务地址。 */
    private String baseUrl = "https://api.deepseek.com/v1";
    /** 模型名称。 */
    private String modelName = "deepseek-chat";
    /** 主 Agent 定义 ID。 */
    private String agentId = "squad-master";
    /** 主 Agent 展示名称。 */
    private String agentName = "主协调员";
    /** 主 Agent 系统提示词。 */
    private String systemPrompt = "你是 Agent Squad 的主协调员。";
    /** Harness 工作区路径。 */
    private String workspace = "./.agentscope/v2";
}