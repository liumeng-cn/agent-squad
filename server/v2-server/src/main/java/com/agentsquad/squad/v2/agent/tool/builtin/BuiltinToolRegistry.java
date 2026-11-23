package com.agentsquad.squad.v2.agent.tool.builtin;

import com.agentsquad.squad.v2.agent.tool.EventTestTool;
import com.agentsquad.squad.v2.bo.BuiltinToolBO;
import io.agentscope.core.tool.AgentTool;
import io.agentscope.core.tool.Toolkit;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * 内置工具登记表
 * <p>
 * 登记可被配置化引用的代码实现，实现标识与 SDK 注解中的工具名一致；ask_user 属于平台默认能力，不在此登记。
 * 默认定义从 SDK 解析出的工具描述中读取，保证配置页展示的参数与实际实现一致。
 */
@Component
public class BuiltinToolRegistry {
    private final Map<String, Supplier<Object>> implementations = new LinkedHashMap<>();

    public BuiltinToolRegistry() {
        implementations.put(CurrentTimeTool.NAME, CurrentTimeTool::new);
        implementations.put("test_event_tool", EventTestTool::new);
    }

    /**
     * 列出内置实现
     */
    public List<BuiltinToolBO> list() {
        return implementations.keySet().stream().map(this::describe).toList();
    }

    /**
     * 查询内置实现定义
     *
     * @return 未登记时返回 null
     */
    public BuiltinToolBO find(String implementationKey) {
        if (implementationKey == null || !implementations.containsKey(implementationKey)) {
            return null;
        }
        return describe(implementationKey);
    }

    /**
     * 创建内置工具实例
     * <p>
     * 每次返回新实例，工具名称仍为实现标识，由调用方按配置包装模型可见名称。
     */
    public AgentTool create(String implementationKey) {
        Supplier<Object> supplier = implementations.get(implementationKey);
        if (supplier == null) {
            throw new IllegalArgumentException("内置工具实现不存在：" + implementationKey);
        }
        Toolkit toolkit = new Toolkit();
        toolkit.registerTool(supplier.get());
        return toolkit.getTool(implementationKey);
    }

    private BuiltinToolBO describe(String implementationKey) {
        AgentTool tool = create(implementationKey);
        BuiltinToolBO definition = new BuiltinToolBO();
        definition.setImplementationKey(implementationKey);
        definition.setExposedName(tool.getName());
        definition.setDescription(tool.getDescription());
        definition.setInputSchema(tool.getParameters());
        definition.setReadOnly(tool.isReadOnly());
        return definition;
    }
}
