package com.agentsquad.squad.v2.service;

import com.agentsquad.squad.common.enums.ApiCode;
import com.agentsquad.squad.common.exceptions.BusinessException;
import com.agentsquad.squad.v2.agent.ActiveMasterRuntime;
import com.agentsquad.squad.v2.agent.ChatAgentFactory;
import com.agentsquad.squad.v2.agent.mcp.McpClientFactory;
import com.agentsquad.squad.v2.agent.mcp.SharedMcpClientHandle;
import com.agentsquad.squad.v2.agent.tool.ConfiguredToolFactory;
import com.agentsquad.squad.v2.agent.tool.LazyMcpTool;
import com.agentsquad.squad.v2.bo.CompiledMasterReleaseBO;
import com.agentsquad.squad.v2.bo.CompiledSubagentSpecBO;
import com.agentsquad.squad.v2.bo.ToolDefinitionBO;
import com.agentsquad.squad.v2.config.properties.ChatAgentProperties;
import com.agentsquad.squad.v2.enums.AgentType;
import com.agentsquad.squad.v2.enums.ConfigStatus;
import com.agentsquad.squad.v2.enums.ToolType;
import com.agentsquad.squad.v2.mapper.AgentMapper;
import com.agentsquad.squad.v2.mapper.AgentReleaseMapper;
import com.agentsquad.squad.v2.mapper.AgentReleaseSubagentMapper;
import com.agentsquad.squad.v2.mapper.AgentReleaseToolMapper;
import com.agentsquad.squad.v2.mapper.AgentVersionMapper;
import com.agentsquad.squad.v2.po.AgentPO;
import com.agentsquad.squad.v2.po.AgentReleasePO;
import com.agentsquad.squad.v2.po.AgentReleaseSubagentPO;
import com.agentsquad.squad.v2.po.AgentReleaseToolPO;
import com.agentsquad.squad.v2.po.AgentVersionPO;
import com.agentsquad.squad.v2.vo.ChatAgentOptionVO;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import io.agentscope.core.middleware.MiddlewareBase;
import io.agentscope.core.tool.AgentTool;
import io.agentscope.harness.agent.HarnessAgent;
import jakarta.annotation.PreDestroy;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 已发布 Master 运行时注册表
 * <p>
 * 负责将数据库中的 Master 发布定格快照（sq_agent_release）编译为可执行的 AgentScope HarnessAgent，
 * 并按 (agentCode, currentReleaseId) 缓存热实例；当 Master 发布新版本时自动热切换并释放旧连接句柄。
 */
@Slf4j
@Service
public class AgentRuntimeRegistry {
    private final ConcurrentMap<String, ActiveMasterRuntime> activeRuntimes = new ConcurrentHashMap<>();

    @Resource
    private AgentMapper agents;

    @Resource
    private AgentVersionMapper agentVersions;

    @Resource
    private AgentReleaseMapper releases;

    @Resource
    private AgentReleaseSubagentMapper releaseSubagents;

    @Resource
    private AgentReleaseToolMapper releaseTools;

    @Resource
    private ToolConfigService toolConfigService;

    @Resource
    private ConfiguredToolFactory configuredToolFactory;

    @Resource
    private McpClientFactory mcpClientFactory;

    @Resource
    private ChatAgentFactory chatAgentFactory;

    @Resource
    private ChatAgentProperties properties;

    @Resource
    private List<MiddlewareBase> chatTracingMiddlewares;

    @Resource
    private ObjectProvider<HarnessAgent> defaultChatAgent;

    /**
     * 列出可供新对话选择的已发布且启用的 Master 列表
     * <p>
     * 若库中尚无任何已发布的启用 Master，回退返回配置文件中的内置默认智能体，保证冷启动开箱可用。
     */
    public List<ChatAgentOptionVO> listAvailableOptions() {
        List<AgentPO> publishedMasters = agents.selectList(Wrappers.<AgentPO>lambdaQuery()
                .eq(AgentPO::getAgentType, AgentType.MASTER)
                .eq(AgentPO::getStatus, ConfigStatus.ENABLED)
                .isNotNull(AgentPO::getCurrentReleaseId)
                .isNull(AgentPO::getDeletedAt)
                .orderByDesc(AgentPO::getLastUpdateTime)
                .orderByDesc(AgentPO::getId));

        List<ChatAgentOptionVO> options = new ArrayList<>();
        for (AgentPO master : publishedMasters) {
            ActiveMasterRuntime runtime = resolveByMaster(master);
            ChatAgentOptionVO option = new ChatAgentOptionVO();
            option.setAgentCode(master.getAgentCode());
            option.setAgentName(master.getAgentName());
            option.setDisplayName(runtime.getDisplayName());
            option.setDescription(runtime.getDescription());
            option.setReleaseNo(runtime.getReleaseNo());
            option.setStandalone(runtime.isStandalone());
            option.setBuiltIn(false);
            options.add(option);
        }

        if (options.isEmpty() && defaultChatAgent.getIfAvailable() != null) {
            options.add(builtInOption());
        }
        return options;
    }

    /**
     * 为首次建档的新对话确定绑定的逻辑 Master 编码
     * <p>
     * 指定了编码时校验该 Master 处于启用且已发布状态；未指定时优先选用最近更新的已发布 Master，否则回退内置默认。
     */
    public String resolveNewConvAgentCode(String requestedAgentCode) {
        String code = requestedAgentCode == null ? "" : requestedAgentCode.trim();
        if (!code.isEmpty()) {
            if (code.equals(properties.getAgentId()) && defaultChatAgent.getIfAvailable() != null) {
                return code;
            }
            AgentPO master = agents.selectOne(Wrappers.<AgentPO>lambdaQuery()
                    .eq(AgentPO::getAgentCode, code)
                    .eq(AgentPO::getAgentType, AgentType.MASTER)
                    .isNull(AgentPO::getDeletedAt));
            if (master == null || master.getStatus() != ConfigStatus.ENABLED || master.getCurrentReleaseId() == null) {
                throw new BusinessException(ApiCode.BAD_REQUEST, "所选 Master 智能体尚未发布或已停用，请刷新后重试");
            }
            resolveByMaster(master);
            return master.getAgentCode();
        }

        List<ChatAgentOptionVO> available = listAvailableOptions();
        if (!available.isEmpty()) {
            return available.get(0).getAgentCode();
        }
        throw new BusinessException(ApiCode.SERVICE_UNAVAILABLE, "当前暂无可用智能体，请先在管理平台发布 Master");
    }

    /**
     * 按对话记录上的逻辑编码解析当前生效的运行时实例
     * <p>
     * 支持内置默认智能体与数据库已发布 Master；若该 Master 刚发布了新 Release，自动加载新快照并淘汰旧缓存。
     */
    public ActiveMasterRuntime resolveRuntime(String agentDefinitionId) {
        if (properties.getAgentId().equals(agentDefinitionId)) {
            HarnessAgent fallback = defaultChatAgent.getIfAvailable();
            if (fallback == null) {
                throw new BusinessException(ApiCode.SERVICE_UNAVAILABLE, "默认聊天智能体未启用");
            }
            return new ActiveMasterRuntime(
                    properties.getAgentId(),
                    properties.getAgentName(),
                    properties.getAgentName(),
                    "系统内置默认主智能体",
                    null,
                    null,
                    true,
                    true,
                    fallback,
                    List.of(),
                    Map.of());
        }

        AgentPO master = agents.selectOne(Wrappers.<AgentPO>lambdaQuery()
                .eq(AgentPO::getAgentCode, agentDefinitionId)
                .eq(AgentPO::getAgentType, AgentType.MASTER)
                .isNull(AgentPO::getDeletedAt));
        if (master == null || master.getCurrentReleaseId() == null) {
            throw new BusinessException(ApiCode.SERVICE_UNAVAILABLE, "对话绑定的智能体不存在或尚未发布");
        }
        return resolveByMaster(master);
    }

    /**
     * 查询指定逻辑编码的友好展示名称（供对话目录列表展示）
     */
    public String displayNameOf(String agentDefinitionId) {
        if (agentDefinitionId == null || agentDefinitionId.isBlank()) {
            return properties.getAgentName();
        }
        if (properties.getAgentId().equals(agentDefinitionId)) {
            return properties.getAgentName();
        }
        ActiveMasterRuntime cached = activeRuntimes.get(agentDefinitionId);
        if (cached != null) {
            return cached.getDisplayName();
        }
        AgentPO master = agents.selectOne(Wrappers.<AgentPO>lambdaQuery()
                .eq(AgentPO::getAgentCode, agentDefinitionId));
        if (master == null) {
            return agentDefinitionId;
        }
        return isBlank(master.getDisplayAlias()) ? master.getAgentName() : master.getDisplayAlias().trim();
    }

    @PreDestroy
    public void shutdown() {
        for (ActiveMasterRuntime runtime : activeRuntimes.values()) {
            runtime.close();
        }
        activeRuntimes.clear();
    }

    private synchronized ActiveMasterRuntime resolveByMaster(AgentPO master) {
        String code = master.getAgentCode();
        Long expectedReleaseId = master.getCurrentReleaseId();
        ActiveMasterRuntime cached = activeRuntimes.get(code);
        if (cached != null && Objects.equals(cached.getReleaseId(), expectedReleaseId)) {
            return cached;
        }

        CompiledMasterReleaseBO compiled = compileRelease(master, expectedReleaseId);
        HarnessAgent builtAgent = chatAgentFactory.createFromRelease(properties, compiled, chatTracingMiddlewares);
        boolean standalone = compiled.getDirectTools().isEmpty() && compiled.getSubagents().isEmpty();

        ActiveMasterRuntime fresh = new ActiveMasterRuntime(
                code,
                master.getAgentName(),
                compiled.getDisplayName(),
                compiled.getDescription(),
                compiled.getReleaseId(),
                compiled.getReleaseNo(),
                standalone,
                false,
                builtAgent,
                compiled.getMcpHandles(),
                compiled.getActivityAliases());

        ActiveMasterRuntime retired = activeRuntimes.put(code, fresh);
        if (retired != null) {
            retired.close();
        }
        log.info("Master发布快照运行时已装载，agentCode={}，releaseNo={}，standalone={}，直接工具={}，子Agent={}",
                code, compiled.getReleaseNo(), standalone, compiled.getDirectTools().size(), compiled.getSubagents().size());
        return fresh;
    }

    private CompiledMasterReleaseBO compileRelease(AgentPO master, long releaseId) {
        AgentReleasePO release = releases.selectOne(Wrappers.<AgentReleasePO>lambdaQuery()
                .eq(AgentReleasePO::getMasterAgentId, master.getAgentId())
                .eq(AgentReleasePO::getReleaseId, releaseId));
        if (release == null) {
            throw new BusinessException(ApiCode.SERVICE_UNAVAILABLE, "Master 生效发布快照缺失");
        }

        AgentVersionPO masterVersion = agentVersions.selectOne(Wrappers.<AgentVersionPO>lambdaQuery()
                .eq(AgentVersionPO::getAgentId, master.getAgentId())
                .eq(AgentVersionPO::getAgentVersionId, release.getMasterVersionId()));
        if (masterVersion == null) {
            throw new BusinessException(ApiCode.SERVICE_UNAVAILABLE, "Master 定格配置版本缺失");
        }

        List<AgentReleaseToolPO> frozenTools = releaseTools.selectList(Wrappers.<AgentReleaseToolPO>lambdaQuery()
                .eq(AgentReleaseToolPO::getReleaseId, releaseId)
                .orderByAsc(AgentReleaseToolPO::getSortNo)
                .orderByAsc(AgentReleaseToolPO::getId));

        List<AgentReleaseSubagentPO> frozenSubs = releaseSubagents.selectList(Wrappers.<AgentReleaseSubagentPO>lambdaQuery()
                .eq(AgentReleaseSubagentPO::getReleaseId, releaseId)
                .orderByAsc(AgentReleaseSubagentPO::getSortNo)
                .orderByAsc(AgentReleaseSubagentPO::getId));

        Map<Long, SharedMcpClientHandle> mcpHandleByVersion = new LinkedHashMap<>();
        Map<String, String> aliases = new LinkedHashMap<>();
        Map<Long, List<AgentTool>> toolsByOwnerAgent = new LinkedHashMap<>();

        for (AgentReleaseToolPO row : frozenTools) {
            ToolDefinitionBO definition = toolConfigService.releaseDefinition(
                    row.getToolId(), row.getToolVersionId(), row.getMcpServerId(), row.getMcpServerVersionId());
            AgentTool executableTool;
            if (definition.getToolType() == ToolType.MCP && row.getMcpServerVersionId() != null) {
                SharedMcpClientHandle handle = mcpHandleByVersion.computeIfAbsent(
                        row.getMcpServerVersionId(),
                        ignored -> new SharedMcpClientHandle(mcpClientFactory, definition.getMcpConnection()));
                executableTool = new LazyMcpTool(definition, configuredToolFactory, handle);
            } else {
                executableTool = configuredToolFactory.create(definition);
            }

            toolsByOwnerAgent.computeIfAbsent(row.getOwnerAgentId(), ignored -> new ArrayList<>()).add(executableTool);
            String alias = isBlank(row.getToolDisplayAlias()) ? row.getToolName() : row.getToolDisplayAlias().trim();
            aliases.putIfAbsent(definition.getExposedName(), alias);
        }

        List<CompiledSubagentSpecBO> subSpecs = new ArrayList<>();
        for (AgentReleaseSubagentPO subRow : frozenSubs) {
            AgentVersionPO subVersion = agentVersions.selectOne(Wrappers.<AgentVersionPO>lambdaQuery()
                    .eq(AgentVersionPO::getAgentId, subRow.getSubagentId())
                    .eq(AgentVersionPO::getAgentVersionId, subRow.getSubagentVersionId()));
            if (subVersion == null) {
                throw new BusinessException(ApiCode.SERVICE_UNAVAILABLE, "定格的子 Agent 配置版本缺失");
            }

            CompiledSubagentSpecBO spec = new CompiledSubagentSpecBO();
            spec.setSubagentId(subRow.getSubagentId());
            spec.setDelegationName(subRow.getDelegationName());
            spec.setDelegationDescription(isBlank(subRow.getDelegationDescription())
                    ? subVersion.getDescription()
                    : subRow.getDelegationDescription());
            String subAlias = isBlank(subRow.getDisplayAlias()) ? subRow.getSubagentName() : subRow.getDisplayAlias().trim();
            spec.setDisplayAlias(subAlias);
            spec.setSystemPrompt(subVersion.getSystemPrompt());
            spec.setMaxIters(subVersion.getMaxIters());
            spec.setTools(toolsByOwnerAgent.getOrDefault(subRow.getSubagentId(), List.of()));
            subSpecs.add(spec);

            aliases.put(subRow.getDelegationName(), subAlias);
        }

        CompiledMasterReleaseBO result = new CompiledMasterReleaseBO();
        result.setAgentId(master.getAgentId());
        result.setAgentCode(master.getAgentCode());
        result.setAgentName(release.getMasterName());
        result.setDisplayName(isBlank(release.getMasterDisplayAlias())
                ? release.getMasterName()
                : release.getMasterDisplayAlias().trim());
        result.setDescription(masterVersion.getDescription() == null ? "" : masterVersion.getDescription());
        result.setReleaseId(release.getReleaseId());
        result.setReleaseNo(release.getReleaseNo());
        result.setSystemPrompt(masterVersion.getSystemPrompt());
        result.setMaxIters(masterVersion.getMaxIters());
        result.setDirectTools(toolsByOwnerAgent.getOrDefault(master.getAgentId(), List.of()));
        result.setSubagents(subSpecs);
        result.setMcpHandles(new ArrayList<>(mcpHandleByVersion.values()));
        result.setActivityAliases(aliases);
        return result;
    }

    private ChatAgentOptionVO builtInOption() {
        ChatAgentOptionVO option = new ChatAgentOptionVO();
        option.setAgentCode(properties.getAgentId());
        option.setAgentName(properties.getAgentName());
        option.setDisplayName(properties.getAgentName());
        option.setDescription("默认主协调员（尚未发布自定义 Master 时自动启用）");
        option.setReleaseNo(null);
        option.setStandalone(true);
        option.setBuiltIn(true);
        return option;
    }

    private static boolean isBlank(String text) {
        return text == null || text.isBlank();
    }
}
