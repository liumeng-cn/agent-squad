package com.agentsquad.squad.v2.bo;

import com.agentsquad.squad.v2.vo.ChatHistoryMessageVO;
import lombok.Getter;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * 聊天日志投影工作状态
 * <p>
 * 仅在一次纯读投影中索引公开消息、模型回复和输入收据，不作为运行或持久化权威。
 */
@Getter
public class ChatProjectionBO {
    /**
     * 按原生发生顺序排列的公开消息。
     */
    private final Map<String, ChatHistoryMessageVO> messages = new LinkedHashMap<>();

    /**
     * 每个轮次最近的助手回复，用于归属工具与轮次终态。
     */
    private final Map<String, ChatHistoryMessageVO> activeReplies = new LinkedHashMap<>();

    /**
     * 已经应用的输入命令。
     */
    private final Set<String> appliedInputs = new LinkedHashSet<>();

    /**
     * 已结束执行的轮次。
     */
    private final Set<String> finishedTurns = new LinkedHashSet<>();

    /**
     * 已开始的提交命令。
     */
    private final Set<String> startedCommands = new LinkedHashSet<>();
}
