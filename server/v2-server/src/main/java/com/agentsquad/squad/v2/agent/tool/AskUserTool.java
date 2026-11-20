package com.agentsquad.squad.v2.agent.tool;

import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
import io.agentscope.core.tool.ToolSuspendException;

import java.util.List;

/**
 * 用户提问工具
 * <p>
 * 聊天模块在缺少必要信息或需要用户选择时挂起原轮次，由 SDK 保存待答事项。
 * 本工具不执行业务操作，答案通过原生交互接口作为工具结果返回。
 */
public class AskUserTool {
    public static final String NAME = "ask_user";

    /**
     * 请求用户回答
     * <p>
     * 校验公开问题与选项后交由 SDK 挂起，不阻塞线程，也不自行保存交互状态。
     */
    @Tool(name = NAME, description = "需要用户补充必要信息、选择方案或确认意愿时使用。"
            + "传入清楚的问题及可选的建议选项，调用后等待真实用户回答。"
            + "不可自行代答；收到答案后继续原任务，用户拒绝时遵守其选择。", readOnly = true)
    public String ask(
            @ToolParam(name = "question", description = "向用户展示的问题，1至2000字") String question,
            @ToolParam(name = "options", description = "可选的建议答案，最多8项，每项最多200字；用户也可以自行输入", required = false) List<String> options) {
        if (question == null || question.isBlank() || question.length() > 2000) {
            throw new IllegalArgumentException("问题须为1至2000字");
        }
        if (options != null) {
            if (options.size() > 8) {
                throw new IllegalArgumentException("建议选项不能超过8项");
            }
            for (String option : options) {
                if (option == null || option.isBlank() || option.length() > 200) {
                    throw new IllegalArgumentException("建议选项须为1至200字");
                }
            }
        }
        throw new ToolSuspendException("等待用户回答");
    }
}
