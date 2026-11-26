package com.agentsquad.squad.v2;

import com.agentsquad.squad.common.exceptions.BusinessException;
import com.agentsquad.squad.v2.agent.ActiveMasterRuntime;
import com.agentsquad.squad.v2.agent.ChatAgentFactory;
import com.agentsquad.squad.v2.bo.AgentToolBindingBO;
import com.agentsquad.squad.v2.bo.CompiledMasterReleaseBO;
import com.agentsquad.squad.v2.bo.PromptSectionsBO;
import com.agentsquad.squad.v2.config.properties.ChatAgentProperties;
import com.agentsquad.squad.v2.dto.AgentDelegationDTO;
import com.agentsquad.squad.v2.dto.AgentSaveDTO;
import com.agentsquad.squad.v2.enums.AgentType;
import com.agentsquad.squad.v2.enums.ConfigReadiness;
import com.agentsquad.squad.v2.enums.ConfigStatus;
import com.agentsquad.squad.v2.enums.ToolType;
import com.agentsquad.squad.v2.mapper.AgentMapper;
import com.agentsquad.squad.v2.mapper.AgentReleaseMapper;
import com.agentsquad.squad.v2.mapper.AgentReleaseSubagentMapper;
import com.agentsquad.squad.v2.mapper.AgentReleaseToolMapper;
import com.agentsquad.squad.v2.mapper.AgentVersionMapper;
import com.agentsquad.squad.v2.po.AgentPO;
import com.agentsquad.squad.v2.po.AgentReleasePO;
import com.agentsquad.squad.v2.po.AgentVersionPO;
import com.agentsquad.squad.v2.service.AgentConfigService;
import com.agentsquad.squad.v2.service.AgentRuntimeRegistry;
import com.agentsquad.squad.v2.utils.ConfigCodeGenerator;
import com.agentsquad.squad.v2.utils.PromptSectionRules;
import com.agentsquad.squad.v2.vo.AgentPublishPreviewVO;
import io.agentscope.harness.agent.HarnessAgent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
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

    @Test
    void allowsStandaloneMasterWithoutAnyToolsOrSubagentsToPublishAndRun() {
        PromptSectionsBO sections = new PromptSectionsBO();
        sections.setRole("你是独立运行的通用顾问");
        sections.setGoal("直接通过对话解答用户的架构与研发问题");

        AgentPO master = new AgentPO();
        master.setAgentId(100L);
        master.setAgentCode("AGT-M-20261124-001");
        master.setAgentName("通用架构顾问");
        master.setDisplayAlias("架构顾问");
        master.setAgentType(AgentType.MASTER);
        master.setStatus(ConfigStatus.ENABLED);
        master.setLatestVersionId(1001L);
        master.setLatestVersionNo(1);
        master.setLatestReadiness(ConfigReadiness.READY);
        master.setRevision(0L);

        AgentVersionPO version = new AgentVersionPO();
        version.setAgentVersionId(1001L);
        version.setAgentId(100L);
        version.setVersionNo(1);
        version.setReadiness(ConfigReadiness.READY);
        version.setDescription("零外部组件独立对话主智能体");
        version.setPromptSections(PromptSectionRules.normalize(sections));
        version.setSystemPrompt(PromptSectionRules.assemble(sections));

        AgentMapper agents = mock(AgentMapper.class);
        AgentVersionMapper versions = mock(AgentVersionMapper.class);
        AgentReleaseMapper releases = mock(AgentReleaseMapper.class);
        when(agents.selectOne(any())).thenReturn(master);
        when(versions.selectOne(any())).thenReturn(version);
        when(versions.selectToolBindings(1001L)).thenReturn(List.of());
        when(versions.selectDelegations(1001L)).thenReturn(List.of());

        AgentConfigService service = new AgentConfigService();
        ReflectionTestUtils.setField(service, "agents", agents);
        ReflectionTestUtils.setField(service, "versions", versions);
        ReflectionTestUtils.setField(service, "releases", releases);

        // 1. 验证零组件 Master 可正常通过发布预检
        AgentPublishPreviewVO preview = service.publishPreview(100L);
        assertThat(preview.isPublishable()).isTrue();
        assertThat(preview.getErrors()).isEmpty();
        assertThat(preview.getDependencies()).hasSize(1);
        assertThat(preview.getDependencies().get(0).getResourceKind()).isEqualTo("MASTER");

        // 2. 验证发布快照被 AgentRuntimeRegistry 编译为 standalone 运行时，并在发布升级后热切换
        master.setCurrentReleaseId(5001L);
        AgentReleasePO release1 = new AgentReleasePO();
        release1.setReleaseId(5001L);
        release1.setMasterAgentId(100L);
        release1.setReleaseNo(1);
        release1.setMasterVersionId(1001L);
        release1.setMasterName("通用架构顾问");
        release1.setMasterDisplayAlias("架构顾问");

        AgentReleaseSubagentMapper releaseSubagents = mock(AgentReleaseSubagentMapper.class);
        AgentReleaseToolMapper releaseTools = mock(AgentReleaseToolMapper.class);
        ChatAgentFactory factory = mock(ChatAgentFactory.class);
        HarnessAgent builtV1 = mock(HarnessAgent.class);
        HarnessAgent builtV2 = mock(HarnessAgent.class);

        when(releases.selectOne(any())).thenReturn(release1);
        when(releaseTools.selectList(any())).thenReturn(List.of());
        when(releaseSubagents.selectList(any())).thenReturn(List.of());
        when(factory.createFromRelease(any(), any(CompiledMasterReleaseBO.class), any()))
                .thenReturn(builtV1, builtV2);

        ChatAgentProperties props = new ChatAgentProperties();
        props.setAgentId("squad-master");
        props.setAgentName("Squad");

        AgentRuntimeRegistry registry = new AgentRuntimeRegistry();
        ReflectionTestUtils.setField(registry, "agents", agents);
        ReflectionTestUtils.setField(registry, "agentVersions", versions);
        ReflectionTestUtils.setField(registry, "releases", releases);
        ReflectionTestUtils.setField(registry, "releaseSubagents", releaseSubagents);
        ReflectionTestUtils.setField(registry, "releaseTools", releaseTools);
        ReflectionTestUtils.setField(registry, "chatAgentFactory", factory);
        ReflectionTestUtils.setField(registry, "properties", props);
        ReflectionTestUtils.setField(registry, "chatTracingMiddlewares", List.of());

        ActiveMasterRuntime runtime1 = registry.resolveRuntime("AGT-M-20261124-001");
        assertThat(runtime1.isStandalone()).isTrue();
        assertThat(runtime1.getDisplayName()).isEqualTo("架构顾问");
        assertThat(runtime1.getReleaseNo()).isEqualTo(1);
        assertThat(runtime1.getAgent()).isSameAs(builtV1);

        // 相同 releaseId 命中缓存，不重复构建
        assertThat(registry.resolveRuntime("AGT-M-20261124-001")).isSameAs(runtime1);
        verify(factory, times(1)).createFromRelease(eq(props), any(), any());

        // 发布 Release #2 后自动热切换并关闭旧实例
        master.setCurrentReleaseId(5002L);
        AgentReleasePO release2 = new AgentReleasePO();
        release2.setReleaseId(5002L);
        release2.setMasterAgentId(100L);
        release2.setReleaseNo(2);
        release2.setMasterVersionId(1001L);
        release2.setMasterName("通用架构顾问");
        release2.setMasterDisplayAlias("架构顾问Pro");
        when(releases.selectOne(any())).thenReturn(release2);

        ActiveMasterRuntime runtime2 = registry.resolveRuntime("AGT-M-20261124-001");
        assertThat(runtime2.getReleaseNo()).isEqualTo(2);
        assertThat(runtime2.getDisplayName()).isEqualTo("架构顾问Pro");
        assertThat(runtime2.getAgent()).isSameAs(builtV2);
        verify(builtV1, times(1)).close();
    }
}
