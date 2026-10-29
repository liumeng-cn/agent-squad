package com.agentsquad.squad.integration.agentscope;

import com.agentsquad.squad.tools.mock.MockLookupTool;
import com.agentsquad.squad.tools.mock.MockTicketCreateTool;
import io.agentscope.core.tool.AgentTool;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 供应商请求前的工具Schema结构回归。
 * 属性位置只能放Schema，required等对象约束必须处于所属Schema层级，避免一次工具错误阻断所有问答。
 */
class ToolSchemaShapeTest {
    @Test
    void allMasterToolsExposeValidObjectAndArraySchemaPositions() {
        List<AgentTool> tools = List.of(new PublishPlanTool(), new CreateGoalTaskTool(), new SubmitTaskDirectiveTool(),
                new ApplyTaskDirectiveTool(), new PrepareContextTool(), new ProposeCompletionTool(),
                new RequestInputTool(), new ListWorkerCapabilitiesTool(), new MockLookupTool(), new MockTicketCreateTool());
        for (AgentTool tool : tools) {
            requireSchema(tool.getParameters());
        }
        var plan = new PublishPlanTool().getParameters();
        assertThat((List<?>) plan.get("required")).hasSize(5);
        assertThat(((Map<?, ?>) plan.get("properties")).containsKey("additionalProperties")).isFalse();
    }

    private void requireSchema(Object value) {
        assertThat(value).isInstanceOf(Map.class);
        Map<?, ?> schema = (Map<?, ?>) value;
        if (schema.get("properties") instanceof Map<?, ?> properties) {
            for (Object child : properties.values()) {
                requireSchema(child);
            }
            if (schema.get("required") instanceof List<?> required) {
                for (Object name : required) {
                    assertThat(properties.containsKey(name)).isTrue();
                }
            }
        }
        if (schema.containsKey("items")) {
            requireSchema(schema.get("items"));
        }
    }
}
