package com.agentsquad.squad.v2.agent.tool;

import com.agentsquad.squad.v2.agent.mcp.McpClientFactory;
import com.agentsquad.squad.v2.agent.tool.http.HttpConfiguredTool;
import com.agentsquad.squad.v2.agent.tool.http.HttpToolTemplate;
import com.agentsquad.squad.v2.bo.ToolDefinitionBO;
import com.agentsquad.squad.v2.bo.ToolRunResultBO;
import com.agentsquad.squad.v2.enums.ToolType;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.agentscope.core.message.ContentBlock;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.message.ToolResultBlock;
import io.agentscope.core.message.ToolResultState;
import io.agentscope.core.message.ToolUseBlock;
import io.agentscope.core.tool.AgentTool;
import io.agentscope.core.tool.ToolCallParam;
import io.agentscope.core.tool.Toolkit;
import io.agentscope.core.tool.mcp.McpClientWrapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 配置化工具试运行
 * <p>
 * 按运行时相同的生成规则构建工具，经 Toolkit 校验参数后调用一次；不写库、不进入 Agent 会话。
 * MCP 工具每次建立临时连接，调用结束后关闭。输出与请求摘要中的凭据已遮蔽。
 */
@Slf4j
@Component
public class ConfiguredToolRunner {
    private static final Duration MAX_WAIT = Duration.ofSeconds(130);

    private static final ObjectMapper JSON = new ObjectMapper();

    @Resource
    private ConfiguredToolFactory toolFactory;

    @Resource
    private McpClientFactory mcpClients;

    /**
     * 试运行工具
     * <p>
     * 参数先按工具 Schema 校验；连接、调用或超时失败都返回失败结果，不抛给接口层。
     */
    public ToolRunResultBO run(ToolDefinitionBO definition, Map<String, Object> arguments) {
        long startedAt = System.nanoTime();
        String request = describeRequest(definition, arguments);
        String credential = credentialOf(definition);

        ToolResultBlock result;
        try {
            if (definition.getToolType() == ToolType.MCP) {
                result = runMcp(definition, arguments);
            } else {
                result = call(toolFactory.create(definition), arguments);
            }
        } catch (RuntimeException failed) {
            String message = failed.getMessage() == null ? failed.getClass().getSimpleName() : failed.getMessage();
            result = ToolResultBlock.error(HttpToolTemplate.mask(message, credential));
        }

        ToolRunResultBO run = new ToolRunResultBO();
        run.setSuccess(result != null && result.getState() != ToolResultState.ERROR);
        run.setDurationMs(Duration.ofNanos(System.nanoTime() - startedAt).toMillis());
        run.setRequest(request);
        run.setOutput(HttpToolTemplate.mask(textOf(result), credential));
        log.info("工具试运行完成，type={}，tool={}，success={}，耗时={}ms",
                definition.getToolType(), definition.getExposedName(), run.isSuccess(), run.getDurationMs());
        return run;
    }

    private ToolResultBlock runMcp(ToolDefinitionBO definition, Map<String, Object> arguments) {
        try (McpClientWrapper client = mcpClients.connect(definition.getMcpConnection())) {
            return call(toolFactory.createMcpTool(definition, client), arguments);
        }
    }

    private ToolResultBlock call(AgentTool tool, Map<String, Object> arguments) {
        Toolkit toolkit = new Toolkit();
        toolkit.registerAgentTool(tool);

        ToolUseBlock toolUse = ToolUseBlock.builder()
                .id("test-run")
                .name(tool.getName())
                .input(arguments)
                .content(toJson(arguments))
                .build();
        ToolCallParam param = ToolCallParam.builder()
                .toolUseBlock(toolUse)
                .input(arguments)
                .build();
        return toolkit.callTool(param).block(MAX_WAIT);
    }

    private String describeRequest(ToolDefinitionBO definition, Map<String, Object> arguments) {
        if (definition.getToolType() == ToolType.HTTP) {
            return new HttpConfiguredTool(definition, null).describeRequest(arguments);
        }
        if (definition.getToolType() == ToolType.MCP) {
            return "MCP " + definition.getMcpServerLabel() + " / " + definition.getRemoteToolName();
        }
        return "内置实现 " + definition.getImplementationKey();
    }

    private String credentialOf(ToolDefinitionBO definition) {
        if (definition.getMcpConnection() != null) {
            return definition.getMcpConnection().getCredential();
        }
        return definition.getCredential();
    }

    private static String textOf(ToolResultBlock result) {
        if (result == null) {
            return "工具没有返回结果";
        }

        List<String> parts = new ArrayList<>();
        for (ContentBlock block : result.getOutput()) {
            if (block instanceof TextBlock text) {
                parts.add(text.getText());
            } else {
                parts.add("[" + block.getClass().getSimpleName() + "]");
            }
        }
        return String.join("\n", parts);
    }

    private static String toJson(Map<String, Object> arguments) {
        try {
            return JSON.writeValueAsString(arguments);
        } catch (JsonProcessingException invalid) {
            throw new IllegalArgumentException("试运行参数无法转换为 JSON");
        }
    }
}
