package com.agentsquad.squad.v2.vo;

import lombok.Data;

/**
 * 聊天页可选 Master 智能体视图
 * <p>
 * 面向普通聊天用户展示所有启用且存在生效发布快照的 Master 智能体，不含敏感系统提示词或凭据信息。
 */
@Data
public class ChatAgentOptionVO {
    /**
     * 逻辑 Agent 编码（如 AGT-M-20261124-001 或内置 squad-master）
     */
    private String agentCode;

    /**
     * 后台配置名称
     */
    private String agentName;

    /**
     * 前端优先展示名称（优先取 displayAlias，为空取 agentName）
     */
    private String displayName;

    /**
     * 智能体职责描述
     */
    private String description;

    /**
     * 当前生效发布序号（内置默认智能体为 null）
     */
    private Integer releaseNo;

    /**
     * 是否为未挂载任何工具与子 Agent 的独立对话模式
     */
    private boolean standalone;

    /**
     * 是否为配置文件内置的默认兜底智能体
     */
    private boolean builtIn;
}
