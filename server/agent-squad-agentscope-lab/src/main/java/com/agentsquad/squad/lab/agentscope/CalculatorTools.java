package com.agentsquad.squad.lab.agentscope;

import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;

/**
 * 给 Agent 调用的本地计算工具。
 *
 * <p>这是一个无外部副作用的示例，用于观察模型如何根据工具描述生成参数。</p>
 */
public class CalculatorTools {

    /**
     * 计算两个整数的和。
     */
    @Tool(
            name = "add_numbers",
            description = "计算两个整数的和。需要进行加法计算时调用此工具。",
            readOnly = true,
            concurrencySafe = true)
    public String addNumbers(
            @ToolParam(name = "left", description = "第一个整数") int left,
            @ToolParam(name = "right", description = "第二个整数") int right) {
        return String.valueOf(left + right);
    }
}
