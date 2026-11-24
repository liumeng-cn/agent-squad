package com.agentsquad.squad.v2;

import com.agentsquad.squad.common.exceptions.BusinessException;
import com.agentsquad.squad.v2.bo.AgentToolBindingBO;
import com.agentsquad.squad.v2.bo.PromptSectionsBO;
import com.agentsquad.squad.v2.dto.AgentDelegationDTO;
import com.agentsquad.squad.v2.dto.AgentSaveDTO;
import com.agentsquad.squad.v2.enums.AgentType;
import com.agentsquad.squad.v2.enums.ConfigReadiness;
import com.agentsquad.squad.v2.enums.ToolType;
import com.agentsquad.squad.v2.mapper.AgentMapper;
import com.agentsquad.squad.v2.mapper.AgentVersionMapper;
import com.agentsquad.squad.v2.po.AgentPO;
import com.agentsquad.squad.v2.service.AgentConfigService;
import com.agentsquad.squad.v2.utils.ConfigCodeGenerator;
import com.agentsquad.squad.v2.utils.PromptSectionRules;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Agent 配置与发布规则单元测试
 * <p>
 * 验证编码自动生成、五段式提示词拼装与就绪判定、工具与委派名称冲突校验以及 Sub 禁止独立发布规则。
 */
class AgentConfigServiceTest {
    @ParameterizedTest
    @ValueSource(strings = {"query_order", "ask_user"})
    void rejectsDelegationNameConflictingWithTools(String name) {
        AgentVersionMapper versions = mock(AgentVersionMapper.class);
        AgentToolBindingBO tool = new AgentToolBindingBO();
        tool.setToolId(20L);
        tool.setToolName("订单查询");
        tool.setExposedName("query_order");
        tool.setReadiness(ConfigReadiness.READY);
        tool.setUsable(true);
        when(versions.selectToolCandidates(List.of(20L))).thenReturn(List.of(tool));

        AgentPO agent = new AgentPO();
        agent.setAgentId(10L);
        agent.setAgentType(AgentType.MASTER);
        AgentDelegationDTO delegation = new AgentDelegationDTO();
        delegation.setSubagentId("30");
        delegation.setDelegationName(name);
        AgentSaveDTO save = new AgentSaveDTO();
        save.setToolIds(List.of("20"));
        save.setSubagents(List.of(delegation));

        AgentConfigService service = new AgentConfigService();
        ReflectionTestUtils.setField(service, "versions", versions);

        assertThatThrownBy(() -> ReflectionTestUtils.invokeMethod(service, "validateBindings", agent, save))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("与模型工具名冲突");
    }

    @Test
    void rejectsBindingIncompleteOrDisabledTool() {
        AgentVersionMapper versions = mock(AgentVersionMapper.class);
        AgentToolBindingBO tool = new AgentToolBindingBO();
        tool.setToolId(21L);
        tool.setToolName("未就绪工具");
        tool.setUsable(false);
        tool.setUnusableReason("工具配置未就绪");
        when(versions.selectToolCandidates(List.of(21L))).thenReturn(List.of(tool));

        AgentPO agent = new AgentPO();
        agent.setAgentId(10L);
        agent.setAgentType(AgentType.MASTER);
        AgentSaveDTO save = new AgentSaveDTO();
        save.setToolIds(List.of("21"));

        AgentConfigService service = new AgentConfigService();
        ReflectionTestUtils.setField(service, "versions", versions);

        assertThatThrownBy(() -> ReflectionTestUtils.invokeMethod(service, "validateBindings", agent, save))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("工具配置未就绪");
    }

    @Test
    void rejectsSubagentPublishPreview() {
        AgentMapper agents = mock(AgentMapper.class);
        AgentPO sub = new AgentPO();
        sub.setAgentId(30L);
        sub.setAgentType(AgentType.SUB_AGENT);
        when(agents.selectOne(any())).thenReturn(sub);

        AgentConfigService service = new AgentConfigService();
        ReflectionTestUtils.setField(service, "agents", agents);

        assertThatThrownBy(() -> service.publishPreview(30L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("仅 Master Agent 允许发布");
    }

    @Test
    void generatesUppercaseHyphenatedDailySequenceCodes() {
        String mcpPrefix = ConfigCodeGenerator.mcpServerPrefix();
        assertThat(ConfigCodeGenerator.nextCode(mcpPrefix, null, 0)).matches("^MCP-\\d{8}-001$");
        assertThat(ConfigCodeGenerator.nextCode(mcpPrefix, mcpPrefix + "009", 0)).matches("^MCP-\\d{8}-010$");
        assertThat(ConfigCodeGenerator.nextCode(ConfigCodeGenerator.toolPrefix(ToolType.BUILTIN), null, 0))
                .matches("^TOOL-B-\\d{8}-001$");
        assertThat(ConfigCodeGenerator.nextCode(ConfigCodeGenerator.toolPrefix(ToolType.HTTP), null, 0))
                .matches("^TOOL-H-\\d{8}-001$");
        assertThat(ConfigCodeGenerator.nextCode(ConfigCodeGenerator.toolPrefix(ToolType.MCP), null, 0))
                .matches("^TOOL-M-\\d{8}-001$");
        assertThat(ConfigCodeGenerator.nextCode(ConfigCodeGenerator.agentPrefix(AgentType.MASTER), null, 0))
                .matches("^AGT-M-\\d{8}-001$");
        assertThat(ConfigCodeGenerator.nextCode(ConfigCodeGenerator.agentPrefix(AgentType.SUB_AGENT), null, 0))
                .matches("^AGT-S-\\d{8}-001$");
    }

    @Test
    void assemblesStructuredPromptSectionsAndEvaluatesReadiness() {
        PromptSectionsBO sections = new PromptSectionsBO();
        sections.setRole("你是企业订单客服助手");
        assertThat(PromptSectionRules.isReady(sections)).isFalse();

        sections.setGoal("准确解答订单状态并调用工具查询物流");
        sections.setConstraints("禁止泄露其他用户隐私");
        PromptSectionsBO normalized = PromptSectionRules.normalize(sections);
        assertThat(PromptSectionRules.isReady(normalized)).isTrue();

        String assembled = PromptSectionRules.assemble(normalized);
        assertThat(assembled).contains("## 角色与定位\n你是企业订单客服助手");
        assertThat(assembled).contains("## 核心目标\n准确解答订单状态并调用工具查询物流");
        assertThat(assembled).contains("## 约束与边界\n禁止泄露其他用户隐私");
        assertThat(assembled).doesNotContain("## 工作流程与策略");
    }
}
