package com.agentsquad.squad.v2.service;

import com.agentsquad.squad.common.enums.ApiCode;
import com.agentsquad.squad.common.exceptions.BusinessException;
import com.agentsquad.squad.v2.agent.tool.AskUserTool;
import com.agentsquad.squad.v2.bo.AgentDelegationBO;
import com.agentsquad.squad.v2.bo.AgentDraftBindingsBO;
import com.agentsquad.squad.v2.bo.AgentPublishPlanBO;
import com.agentsquad.squad.v2.bo.AgentSummaryBO;
import com.agentsquad.squad.v2.bo.AgentToolBindingBO;
import com.agentsquad.squad.v2.bo.ConfigUsageBO;
import com.agentsquad.squad.v2.bo.PromptSectionsBO;
import com.agentsquad.squad.v2.converter.AgentConfigConverter;
import com.agentsquad.squad.v2.dto.AgentDelegationDTO;
import com.agentsquad.squad.v2.dto.AgentPublishDTO;
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
import com.agentsquad.squad.v2.mapper.AgentVersionSubagentMapper;
import com.agentsquad.squad.v2.mapper.AgentVersionToolMapper;
import com.agentsquad.squad.v2.po.AgentPO;
import com.agentsquad.squad.v2.po.AgentReleasePO;
import com.agentsquad.squad.v2.po.AgentReleaseSubagentPO;
import com.agentsquad.squad.v2.po.AgentReleaseToolPO;
import com.agentsquad.squad.v2.po.AgentVersionPO;
import com.agentsquad.squad.v2.po.AgentVersionSubagentPO;
import com.agentsquad.squad.v2.po.AgentVersionToolPO;
import com.agentsquad.squad.v2.utils.ConfigCodeGenerator;
import com.agentsquad.squad.v2.utils.ConfigRules;
import com.agentsquad.squad.v2.utils.PromptSectionRules;
import com.agentsquad.squad.v2.vo.AgentConfigVO;
import com.agentsquad.squad.v2.vo.AgentConfigVersionVO;
import com.agentsquad.squad.v2.vo.AgentDetailVO;
import com.agentsquad.squad.v2.vo.AgentPublishDependencyVO;
import com.agentsquad.squad.v2.vo.AgentPublishPreviewVO;
import com.agentsquad.squad.v2.vo.AgentReleaseDetailVO;
import com.agentsquad.squad.v2.vo.AgentReleaseSubagentDetailVO;
import com.agentsquad.squad.v2.vo.AgentReleaseToolDetailVO;
import com.agentsquad.squad.v2.vo.SubagentOptionVO;
import com.agentsquad.squad.v2.vo.ToolOptionVO;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Agent 配置与 Master 发布定格管理
 * <p>
 * 维护 Master 与 Sub 的目录、配置版本历史、工具与委派关联，以及仅面向 Master 的发布定格快照。
 * 每次保存产生新的不可变配置版本且不改变已发布快照；仅 Master 发布时冻结全链路版本图。
 */
@Slf4j
@Service
public class AgentConfigService {
    private static final String CONFLICT = "Agent 已被其他操作修改，请刷新后重试";

    private static final int MAX_CODE_RETRIES = 5;

    @Resource
    private AgentMapper agents;

    @Resource
    private AgentVersionMapper versions;

    @Resource
    private AgentVersionToolMapper versionTools;

    @Resource
    private AgentVersionSubagentMapper versionSubagents;

    @Resource
    private AgentReleaseMapper releases;

    @Resource
    private AgentReleaseSubagentMapper releaseSubagents;

    @Resource
    private AgentReleaseToolMapper releaseTools;

    @Resource
    private AgentConfigConverter converter;

    @Resource
    private ToolConfigService toolConfig;

    @Resource
    private McpServerConfigService mcpServers;

    /**
     * 查询 Agent 列表
     */
    public List<AgentConfigVO> list() {
        return agents.selectSummaries(null).stream().map(converter::toView).toList();
    }

    /**
     * 查询 Agent 详情
     * <p>
     * 返回目录摘要、最新配置版本、历史配置版本清单；Master 额外返回发布历史，Sub 额外返回上游引用关系。
     */
    public AgentDetailVO detail(long agentId) {
        AgentConfigVO agent = summary(agentId);
        AgentDetailVO detail = new AgentDetailVO();
        detail.setAgent(agent);
        if (agent.getLatestVersionId() != null) {
            long latestVersionId = ConfigRules.parseId(agent.getLatestVersionId(), "Agent 版本ID");
            detail.setLatestVersion(readVersion(agentId, latestVersionId));
        }
        detail.setVersions(versions.selectSummaries(agentId).stream().map(converter::toView).toList());
        if (agent.getAgentType() == AgentType.MASTER) {
            detail.setReleases(releases.selectSummaries(agentId).stream().map(converter::toView).toList());
        } else {
            detail.setUsages(agents.selectUsages(agentId).stream().map(converter::toView).toList());
        }
        return detail;
    }

    /**
     * 新增 Agent
     * <p>
     * 系统自动生成带日期的随机编码，同一事务写入目录、第 1 个配置版本及工具/委派关联。
     */
    @Transactional
    public AgentDetailVO create(AgentSaveDTO request) {
        if (request.getAgentType() == null) {
            throw new BusinessException(ApiCode.BAD_REQUEST, "新增 Agent 必须指定类型");
        }
        long actorId = ConfigRules.actor();
        PromptSectionsBO sections = PromptSectionRules.normalize(converter.toSections(request.getPromptSections()));
        String description = trimToEmpty(request.getDescription());

        AgentPO agent = new AgentPO();
        agent.setAgentId(IdWorker.getId());
        agent.setAgentName(request.getAgentName().trim());
        agent.setDisplayAlias(trimToEmpty(request.getDisplayAlias()));
        agent.setAgentType(request.getAgentType());
        agent.setStatus(ConfigStatus.ENABLED);
        agent.setLatestVersionNo(0);
        agent.setLatestReadiness(ConfigReadiness.INCOMPLETE);
        agent.setRevision(0L);
        agent.setCreatorId(actorId);
        agent.setLastUpdaterId(actorId);

        AgentDraftBindingsBO bindings = validateBindings(agent, request);
        ConfigReadiness readiness = evaluateReadiness(agent.getAgentType(), description, sections);

        insertWithGeneratedCode(agent);
        long versionId = insertVersion(agent.getAgentId(), 1, readiness, description, sections, request.getMaxIters(), actorId);
        insertVersionBindings(versionId, bindings, actorId);

        AgentPO pointer = new AgentPO();
        pointer.setAgentId(agent.getAgentId());
        pointer.setAgentName(agent.getAgentName());
        pointer.setDisplayAlias(agent.getDisplayAlias());
        pointer.setLatestVersionId(versionId);
        pointer.setLatestVersionNo(1);
        pointer.setLatestReadiness(readiness);
        pointer.setRevision(0L);
        pointer.setLastUpdaterId(actorId);
        ConfigRules.requireChanged(agents.updateLatest(pointer), CONFLICT);

        log.info("Agent已创建，agentId={}，code={}，type={}，readiness={}",
                agent.getAgentId(), agent.getAgentCode(), agent.getAgentType(), readiness);
        return detail(agent.getAgentId());
    }

    /**
     * 保存 Agent 改动
     * <p>
     * 每次保存生成新的不可变配置版本并按目录修订号条件更新最新指针，不改变已发布的 Master 快照。
     */
    @Transactional
    public AgentDetailVO save(long agentId, AgentSaveDTO request) {
        if (request.getRevision() == null) {
            throw new BusinessException(ApiCode.BAD_REQUEST, "保存配置需要携带修订号");
        }
        AgentPO existing = requireAgent(agentId);
        long actorId = ConfigRules.actor();
        PromptSectionsBO sections = PromptSectionRules.normalize(converter.toSections(request.getPromptSections()));
        String description = trimToEmpty(request.getDescription());

        AgentDraftBindingsBO bindings = validateBindings(existing, request);
        ConfigReadiness readiness = evaluateReadiness(existing.getAgentType(), description, sections);

        int nextVersionNo = versions.nextVersionNo(agentId);
        long versionId = insertVersion(agentId, nextVersionNo, readiness, description, sections, request.getMaxIters(), actorId);
        insertVersionBindings(versionId, bindings, actorId);

        AgentPO update = new AgentPO();
        update.setAgentId(agentId);
        update.setAgentName(request.getAgentName().trim());
        update.setDisplayAlias(trimToEmpty(request.getDisplayAlias()));
        update.setLatestVersionId(versionId);
        update.setLatestVersionNo(nextVersionNo);
        update.setLatestReadiness(readiness);
        update.setRevision(request.getRevision());
        update.setLastUpdaterId(actorId);
        ConfigRules.requireChanged(agents.updateLatest(update), CONFLICT);

        log.info("Agent已保存新配置版本，agentId={}，versionNo={}，readiness={}", agentId, nextVersionNo, readiness);
        return detail(agentId);
    }

    /**
     * 启用或停用 Agent
     */
    @Transactional
    public AgentConfigVO changeStatus(long agentId, ConfigStatus status, long revision) {
        AgentPO existing = requireAgent(agentId);

        AgentPO update = new AgentPO();
        update.setAgentId(agentId);
        update.setStatus(status);
        update.setRevision(revision);
        update.setLastUpdaterId(ConfigRules.actor());
        ConfigRules.requireChanged(agents.updateStatus(update), CONFLICT);

        if (status == ConfigStatus.DISABLED && existing.getAgentType() == AgentType.SUB_AGENT) {
            requireNoActiveUsage(agentId, "停用");
        }

        log.info("Agent状态已更新，agentId={}，status={}", agentId, status);
        return summary(agentId);
    }

    /**
     * 逻辑删除 Agent
     * <p>
     * 保留历史配置版本与 Master 发布快照；若 Sub 仍被未删除 Master 引用则拒绝删除。
     */
    @Transactional
    public void delete(long agentId, long revision) {
        AgentPO existing = requireAgent(agentId);
        if (existing.getAgentType() == AgentType.SUB_AGENT) {
            requireNoAnyUsage(agentId);
        }

        AgentPO update = new AgentPO();
        update.setAgentId(agentId);
        update.setRevision(revision);
        update.setLastUpdaterId(ConfigRules.actor());
        ConfigRules.requireChanged(agents.softDelete(update), CONFLICT);
        log.info("Agent已逻辑删除，agentId={}", agentId);
    }

    /**
     * 只读读取指定历史配置版本
     */
    public AgentConfigVersionVO readVersion(long agentId, long versionId) {
        AgentVersionPO version = requireVersion(agentId, versionId);
        AgentConfigVersionVO view = converter.toView(version);
        if (view.getPromptSections() == null) {
            view.setPromptSections(new com.agentsquad.squad.v2.vo.PromptSectionsVO());
        }
        view.setTools(versions.selectToolBindings(versionId).stream().map(converter::toView).toList());
        view.setSubagents(versions.selectDelegations(versionId).stream().map(converter::toView).toList());
        return view;
    }

    /**
     * Master 发布预检
     * <p>
     * 递归解析 Master 最新配置版本及整条依赖链（Sub、Tool、MCP），返回即将定格的清单和版本指纹。
     */
    public AgentPublishPreviewVO publishPreview(long agentId) {
        AgentPublishPlanBO plan = buildPublishPlan(agentId);

        AgentPublishPreviewVO preview = new AgentPublishPreviewVO();
        preview.setAgentId(ConfigRules.text(plan.getMaster().getAgentId()));
        preview.setAgentName(plan.getMaster().getAgentName());
        preview.setRevision(plan.getMaster().getRevision());
        preview.setMasterVersionNo(plan.getMasterVersion() == null ? null : plan.getMasterVersion().getVersionNo());
        preview.setCurrentReleaseNo(plan.getCurrentReleaseNo());
        preview.setDependencyDigest(plan.getDependencyDigest());
        preview.setPublishable(plan.isPublishable());
        preview.setErrors(plan.getErrors());
        preview.setDependencies(plan.getDependencies());
        return preview;
    }

    /**
     * 确认发布 Master 并定格快照
     * <p>
     * 支持按 publishOperationId 幂等；事务内复核依赖指纹与所有依赖的启用就绪状态，写入不可变 Release 快照并切换生效指针。
     */
    @Transactional
    public AgentConfigVO publish(long agentId, AgentPublishDTO request) {
        AgentPO master = requireAgent(agentId);
        if (master.getAgentType() != AgentType.MASTER) {
            throw new BusinessException(ApiCode.BAD_REQUEST, "仅 Master Agent 允许发布，子 Agent 不支持单独发布");
        }

        // 幂等检查：相同发布操作标识已成功定格时直接返回当前摘要
        AgentReleasePO existingRelease = releases.selectOne(Wrappers.<AgentReleasePO>lambdaQuery()
                .eq(AgentReleasePO::getMasterAgentId, agentId)
                .eq(AgentReleasePO::getPublishOperationId, request.getPublishOperationId()));
        if (existingRelease != null) {
            return summary(agentId);
        }

        AgentPublishPlanBO plan = buildPublishPlan(agentId);
        if (!plan.isPublishable()) {
            throw new BusinessException(ApiCode.BAD_REQUEST, "发布校验未通过：" + String.join("；", plan.getErrors()));
        }
        if (!plan.getDependencyDigest().equals(request.getDependencyDigest())) {
            throw new BusinessException(ApiCode.CONFLICT, "发布确认期间依赖配置已发生变化，请刷新确认清单后重试");
        }

        // 按固定顺序复核全部依赖仍处于启用且就绪状态，使用乐观条件更新
        for (Long toolId : plan.getToolIds()) {
            toolConfig.lockEnabledReady(toolId);
        }
        for (Long mcpServerId : plan.getMcpServerIds()) {
            mcpServers.lockEnabledReady(mcpServerId);
        }
        for (Long subagentId : plan.getSubagentIds()) {
            if (agents.confirmEnabledReady(subagentId) != 1) {
                throw new BusinessException(ApiCode.CONFLICT, "依赖的子 Agent 已停用、删除或未就绪，请刷新后重试");
            }
        }

        long actorId = ConfigRules.actor();
        long releaseId = IdWorker.getId();
        int nextReleaseNo = releases.nextReleaseNo(agentId);

        AgentReleasePO release = new AgentReleasePO();
        release.setReleaseId(releaseId);
        release.setMasterAgentId(agentId);
        release.setReleaseNo(nextReleaseNo);
        release.setMasterVersionId(plan.getMasterVersion().getAgentVersionId());
        release.setMasterName(master.getAgentName());
        release.setMasterDisplayAlias(trimToEmpty(master.getDisplayAlias()));
        release.setDependencyDigest(plan.getDependencyDigest());
        release.setPublishOperationId(request.getPublishOperationId());
        release.setCreatorId(actorId);
        release.setLastUpdaterId(actorId);
        releases.insert(release);

        if (!plan.getReleaseSubagents().isEmpty()) {
            for (AgentReleaseSubagentPO row : plan.getReleaseSubagents()) {
                row.setReleaseId(releaseId);
                row.setCreatorId(actorId);
                row.setLastUpdaterId(actorId);
            }
            releaseSubagents.insertBatch(plan.getReleaseSubagents());
        }

        if (!plan.getReleaseTools().isEmpty()) {
            for (AgentReleaseToolPO row : plan.getReleaseTools()) {
                row.setReleaseId(releaseId);
                row.setCreatorId(actorId);
                row.setLastUpdaterId(actorId);
            }
            releaseTools.insertBatch(plan.getReleaseTools());
        }

        AgentPO switchPointer = new AgentPO();
        switchPointer.setAgentId(agentId);
        switchPointer.setCurrentReleaseId(releaseId);
        switchPointer.setRevision(request.getRevision());
        switchPointer.setLastUpdaterId(actorId);
        ConfigRules.requireChanged(agents.switchRelease(switchPointer), "Master 已停用或已被其他操作修改，请刷新后重试");

        log.info("Master已发布定格，agentId={}，releaseNo={}，masterVersionNo={}",
                agentId, nextReleaseNo, plan.getMasterVersion().getVersionNo());
        return summary(agentId);
    }

    /**
     * 只读查询 Master 发布快照详情
     */
    public AgentReleaseDetailVO readRelease(long agentId, long releaseId) {
        AgentPO master = requireAgentIncludingDeleted(agentId);
        AgentReleasePO release = requireRelease(agentId, releaseId);
        AgentVersionPO version = requireVersion(agentId, release.getMasterVersionId());

        AgentReleaseDetailVO view = new AgentReleaseDetailVO();
        view.setReleaseId(ConfigRules.text(release.getReleaseId()));
        view.setReleaseNo(release.getReleaseNo());
        view.setMasterAgentId(ConfigRules.text(agentId));
        view.setMasterName(release.getMasterName());
        view.setMasterDisplayAlias(release.getMasterDisplayAlias());
        view.setMasterVersionId(ConfigRules.text(version.getAgentVersionId()));
        view.setMasterVersionNo(version.getVersionNo());
        view.setDescription(version.getDescription());
        view.setPromptSections(converter.toView(version.getPromptSections()));
        view.setSystemPrompt(version.getSystemPrompt());
        view.setMaxIters(version.getMaxIters());
        view.setDependencyDigest(release.getDependencyDigest());
        view.setPublishedAt(release.getPublishedAt());
        view.setCurrent(release.getReleaseId().equals(master.getCurrentReleaseId()));
        view.setTools(releases.selectReleaseTools(releaseId, agentId).stream().map(converter::toView).toList());
        view.setSubagents(releases.selectReleaseSubagents(releaseId).stream().map(converter::toView).toList());
        return view;
    }

    /**
     * 沿发布快照上下文只读查看定格的 Sub 详情及其工具
     */
    public AgentReleaseSubagentDetailVO readReleaseSubagent(long agentId, long releaseId, long subagentId) {
        AgentReleasePO release = requireRelease(agentId, releaseId);
        AgentReleaseSubagentPO snapshot = releaseSubagents.selectOne(Wrappers.<AgentReleaseSubagentPO>lambdaQuery()
                .eq(AgentReleaseSubagentPO::getReleaseId, releaseId)
                .eq(AgentReleaseSubagentPO::getSubagentId, subagentId));
        if (snapshot == null) {
            throw new BusinessException(ApiCode.NOT_FOUND, "该发布快照未绑定指定子 Agent");
        }
        AgentPO sub = requireAgentIncludingDeleted(subagentId);
        AgentVersionPO version = requireVersion(subagentId, snapshot.getSubagentVersionId());

        AgentReleaseSubagentDetailVO view = new AgentReleaseSubagentDetailVO();
        view.setReleaseId(ConfigRules.text(release.getReleaseId()));
        view.setReleaseNo(release.getReleaseNo());
        view.setPublishedAt(release.getPublishedAt());
        view.setSubagentId(ConfigRules.text(subagentId));
        view.setSubagentCode(sub.getAgentCode());
        view.setSubagentName(snapshot.getSubagentName());
        view.setDisplayAlias(snapshot.getDisplayAlias());
        view.setDelegationName(snapshot.getDelegationName());
        view.setDelegationDescription(snapshot.getDelegationDescription());
        view.setSubagentVersionId(ConfigRules.text(version.getAgentVersionId()));
        view.setVersionNo(version.getVersionNo());
        view.setDescription(version.getDescription());
        view.setPromptSections(converter.toView(version.getPromptSections()));
        view.setSystemPrompt(version.getSystemPrompt());
        view.setMaxIters(version.getMaxIters());
        view.setTools(releases.selectReleaseTools(releaseId, subagentId).stream().map(converter::toView).toList());
        return view;
    }

    /**
     * 沿发布快照上下文只读查看定格的工具与 MCP 详情
     */
    public AgentReleaseToolDetailVO readReleaseTool(long agentId, long releaseId, long toolId) {
        AgentReleasePO release = requireRelease(agentId, releaseId);
        List<AgentReleaseToolPO> matched = releaseTools.selectList(Wrappers.<AgentReleaseToolPO>lambdaQuery()
                .eq(AgentReleaseToolPO::getReleaseId, releaseId)
                .eq(AgentReleaseToolPO::getToolId, toolId));
        if (matched.isEmpty()) {
            throw new BusinessException(ApiCode.NOT_FOUND, "该发布快照未绑定指定工具");
        }
        AgentReleaseToolPO snapshot = matched.get(0);

        AgentReleaseToolDetailVO view = new AgentReleaseToolDetailVO();
        view.setReleaseId(ConfigRules.text(release.getReleaseId()));
        view.setReleaseNo(release.getReleaseNo());
        view.setPublishedAt(release.getPublishedAt());
        view.setToolId(ConfigRules.text(toolId));
        view.setToolName(snapshot.getToolName());
        view.setDisplayAlias(snapshot.getToolDisplayAlias());
        view.setToolVersion(toolConfig.readVersionById(snapshot.getToolVersionId()));
        if (snapshot.getMcpServerVersionId() != null) {
            view.setMcpServerName(snapshot.getMcpServerName());
            view.setMcpVersion(mcpServers.readVersionById(snapshot.getMcpServerVersionId()));
        }
        return view;
    }

    /**
     * 列出可关联的可用工具
     */
    public List<ToolOptionVO> toolOptions() {
        return toolConfig.usableOptions();
    }

    /**
     * 列出可委派的可用子 Agent
     */
    public List<SubagentOptionVO> subagentOptions() {
        return versions.selectSubagentOptions().stream().map(converter::toView).toList();
    }

    /**
     * 校验保存提交的工具与委派关联
     * <p>
     * 保存时不校验当前 Agent 自身必填项，但所选关联对象必须处于未删除、启用且配置就绪状态，且命名无冲突。
     */
    private AgentDraftBindingsBO validateBindings(AgentPO agent, AgentSaveDTO request) {
        List<Long> toolIds = distinctIds(request.getToolIds(), "工具ID");
        List<AgentToolBindingBO> tools = toolCandidates(toolIds);
        requireDistinctToolNames(tools);

        if (agent.getAgentType() == AgentType.SUB_AGENT && !request.getSubagents().isEmpty()) {
            throw new BusinessException(ApiCode.BAD_REQUEST, "子 Agent 不能再委派其他 Agent");
        }

        Set<String> toolNames = tools.stream()
                .map(AgentToolBindingBO::getExposedName)
                .filter(name -> name != null && !name.isBlank())
                .collect(Collectors.toSet());
        Set<String> delegationNames = new HashSet<>();
        List<AgentVersionSubagentPO> delegationRows = new ArrayList<>();
        int sortNo = 0;
        for (AgentDelegationDTO delegation : request.getSubagents()) {
            if (AskUserTool.NAME.equals(delegation.getDelegationName())
                    || toolNames.contains(delegation.getDelegationName())) {
                throw new BusinessException(ApiCode.BAD_REQUEST, "委派名称 " + delegation.getDelegationName() + " 与模型工具名冲突");
            }
            if (!delegationNames.add(delegation.getDelegationName())) {
                throw new BusinessException(ApiCode.BAD_REQUEST, "委派名称 " + delegation.getDelegationName() + " 重复");
            }
            AgentVersionSubagentPO row = new AgentVersionSubagentPO();
            row.setSubagentId(ConfigRules.parseId(delegation.getSubagentId(), "子 Agent ID"));
            row.setDelegationName(delegation.getDelegationName());
            row.setDelegationDescription(blankToNull(delegation.getDelegationDescription()));
            row.setDisplayAlias(trimToEmpty(delegation.getDisplayAlias()));
            row.setSortNo(sortNo++);
            delegationRows.add(row);
        }

        List<Long> subagentIds = delegationRows.stream().map(AgentVersionSubagentPO::getSubagentId).toList();
        Map<Long, AgentDelegationBO> candidates = delegationCandidates(subagentIds);
        Set<Long> seenSubagents = new HashSet<>();
        for (Long subagentId : subagentIds) {
            AgentDelegationBO candidate = candidates.get(subagentId);
            if (candidate.getSubagentType() != AgentType.SUB_AGENT) {
                throw new BusinessException(ApiCode.BAD_REQUEST, candidate.getSubagentName() + " 不是子 Agent，不能被委派");
            }
            if (!candidate.isUsable()) {
                throw new BusinessException(ApiCode.BAD_REQUEST,
                        "子 Agent " + candidate.getSubagentName() + " 不可关联：" + candidate.getUnusableReason());
            }
            if (subagentId.equals(agent.getAgentId()) || !seenSubagents.add(subagentId)) {
                throw new BusinessException(ApiCode.BAD_REQUEST, "同一子 Agent 只能委派一次：" + candidate.getSubagentName());
            }
        }

        AgentDraftBindingsBO bindings = new AgentDraftBindingsBO();
        bindings.setToolIds(toolIds);
        bindings.setDelegations(delegationRows);
        return bindings;
    }

    private ConfigReadiness evaluateReadiness(AgentType type, String description, PromptSectionsBO sections) {
        if (!PromptSectionRules.isReady(sections)) {
            return ConfigReadiness.INCOMPLETE;
        }
        if (type == AgentType.SUB_AGENT && description.isBlank()) {
            return ConfigReadiness.INCOMPLETE;
        }
        return ConfigReadiness.READY;
    }

    private AgentPublishPlanBO buildPublishPlan(long agentId) {
        AgentPO master = requireAgent(agentId);
        if (master.getAgentType() != AgentType.MASTER) {
            throw new BusinessException(ApiCode.BAD_REQUEST, "仅 Master Agent 允许发布，子 Agent 不支持单独发布");
        }
        AgentPublishPlanBO plan = new AgentPublishPlanBO();
        plan.setMaster(master);
        if (master.getCurrentReleaseId() != null) {
            AgentReleasePO current = releases.selectOne(Wrappers.<AgentReleasePO>lambdaQuery()
                    .eq(AgentReleasePO::getReleaseId, master.getCurrentReleaseId()));
            if (current != null) {
                plan.setCurrentReleaseNo(current.getReleaseNo());
            }
        }
        if (master.getStatus() != ConfigStatus.ENABLED) {
            plan.getErrors().add("Master 已停用，请先启用后再发布");
        }
        if (master.getLatestVersionId() == null) {
            plan.getErrors().add("Master 尚未保存有效配置版本");
            plan.setDependencyDigest("");
            return plan;
        }

        AgentVersionPO masterVersion = requireVersion(agentId, master.getLatestVersionId());
        plan.setMasterVersion(masterVersion);
        if (!PromptSectionRules.isReady(masterVersion.getPromptSections())) {
            plan.getErrors().add("Master 系统提示词的角色与定位、核心目标不能为空");
        }

        List<String> digestParts = new ArrayList<>();
        digestParts.add("MASTER:" + master.getAgentId() + ":" + masterVersion.getAgentVersionId() + ":" + master.getRevision());
        plan.getDependencies().add(dependencyNode("MASTER", master.getAgentId(), master.getAgentName(),
                masterVersion.getVersionNo(), master.getDisplayAlias(), "主智能体"));

        // 1. 解析 Master 直接工具
        Set<String> masterToolNames = new HashSet<>();
        List<AgentToolBindingBO> directTools = versions.selectToolBindings(masterVersion.getAgentVersionId());
        int toolSort = 0;
        for (AgentToolBindingBO tool : directTools) {
            collectToolForRelease(plan, digestParts, master.getAgentId(), masterVersion.getAgentVersionId(),
                    "Master 直接工具", tool, masterToolNames, toolSort++);
        }

        // 2. 解析 Master 委派的子 Agent 及其工具
        Set<String> delegationNames = new HashSet<>();
        List<AgentDelegationBO> delegations = versions.selectDelegations(masterVersion.getAgentVersionId());
        int subSort = 0;
        for (AgentDelegationBO delegation : delegations) {
            if (!delegation.isUsable()) {
                plan.getErrors().add("子 Agent " + delegation.getSubagentName() + " 不可用：" + delegation.getUnusableReason());
                continue;
            }
            if (AskUserTool.NAME.equals(delegation.getDelegationName())
                    || masterToolNames.contains(delegation.getDelegationName())) {
                plan.getErrors().add("委派名称 " + delegation.getDelegationName() + " 与 Master 工具名冲突");
                continue;
            }
            if (!delegationNames.add(delegation.getDelegationName())) {
                plan.getErrors().add("委派名称 " + delegation.getDelegationName() + " 重复");
                continue;
            }

            plan.getSubagentIds().add(delegation.getSubagentId());
            String effectiveAlias = !trimToEmpty(delegation.getDisplayAlias()).isBlank()
                    ? delegation.getDisplayAlias().trim()
                    : (!trimToEmpty(delegation.getSubagentDisplayAlias()).isBlank()
                    ? delegation.getSubagentDisplayAlias().trim()
                    : delegation.getSubagentName());

            AgentReleaseSubagentPO subSnapshot = new AgentReleaseSubagentPO();
            subSnapshot.setSubagentId(delegation.getSubagentId());
            subSnapshot.setSubagentVersionId(delegation.getSubagentVersionId());
            subSnapshot.setSubagentName(delegation.getSubagentName());
            subSnapshot.setDelegationName(delegation.getDelegationName());
            subSnapshot.setDelegationDescription(delegation.getDelegationDescription());
            subSnapshot.setDisplayAlias(effectiveAlias);
            subSnapshot.setSortNo(subSort++);
            plan.getReleaseSubagents().add(subSnapshot);

            digestParts.add("SUB:" + delegation.getSubagentId() + ":" + delegation.getSubagentVersionId()
                    + ":" + delegation.getDelegationName());
            plan.getDependencies().add(dependencyNode("SUB_AGENT", delegation.getSubagentId(),
                    delegation.getSubagentName(), delegation.getVersionNo(), effectiveAlias,
                    "委派名：" + delegation.getDelegationName()));

            // 解析该 Sub 的工具
            Set<String> subToolNames = new HashSet<>();
            List<AgentToolBindingBO> subTools = versions.selectToolBindings(delegation.getSubagentVersionId());
            int subToolSort = 0;
            for (AgentToolBindingBO subTool : subTools) {
                collectToolForRelease(plan, digestParts, delegation.getSubagentId(), delegation.getSubagentVersionId(),
                        "Sub「" + delegation.getSubagentName() + "」工具", subTool, subToolNames, subToolSort++);
            }
        }

        Collections.sort(digestParts);
        plan.setDependencyDigest(sha256(String.join("|", digestParts)));
        return plan;
    }

    private void collectToolForRelease(AgentPublishPlanBO plan, List<String> digestParts,
                                       long ownerAgentId, long ownerVersionId, String scope,
                                       AgentToolBindingBO tool, Set<String> scopeToolNames, int sortNo) {
        if (!tool.isUsable()) {
            plan.getErrors().add(scope + "「" + tool.getToolName() + "」不可用：" + tool.getUnusableReason());
            return;
        }
        if (AskUserTool.NAME.equals(tool.getExposedName())) {
            plan.getErrors().add(scope + "「" + tool.getToolName() + "」使用了平台保留名称 ask_user");
            return;
        }
        if (!scopeToolNames.add(tool.getExposedName())) {
            plan.getErrors().add(scope + "存在重复的模型工具名：" + tool.getExposedName());
            return;
        }

        plan.getToolIds().add(tool.getToolId());
        String effectiveAlias = trimToEmpty(tool.getDisplayAlias()).isBlank()
                ? tool.getToolName()
                : tool.getDisplayAlias().trim();

        AgentReleaseToolPO toolSnapshot = new AgentReleaseToolPO();
        toolSnapshot.setOwnerAgentId(ownerAgentId);
        toolSnapshot.setOwnerAgentVersionId(ownerVersionId);
        toolSnapshot.setToolId(tool.getToolId());
        toolSnapshot.setToolVersionId(tool.getToolVersionId());
        toolSnapshot.setToolName(tool.getToolName());
        toolSnapshot.setToolDisplayAlias(effectiveAlias);
        toolSnapshot.setSortNo(sortNo);

        if (tool.getToolType() == ToolType.MCP && tool.getMcpServerId() != null) {
            plan.getMcpServerIds().add(tool.getMcpServerId());
            toolSnapshot.setMcpServerId(tool.getMcpServerId());
            toolSnapshot.setMcpServerVersionId(tool.getMcpServerVersionId());
            toolSnapshot.setMcpServerName(tool.getMcpServerName());

            digestParts.add("MCP:" + tool.getMcpServerId() + ":" + tool.getMcpServerVersionId());
            plan.getDependencies().add(dependencyNode("MCP", tool.getMcpServerId(),
                    tool.getMcpServerName(), null, "", "工具「" + tool.getToolName() + "」依赖"));
        }

        plan.getReleaseTools().add(toolSnapshot);
        digestParts.add("TOOL:" + ownerAgentId + ":" + tool.getToolId() + ":" + tool.getToolVersionId());
        plan.getDependencies().add(dependencyNode("TOOL", tool.getToolId(), tool.getToolName(),
                tool.getVersionNo(), effectiveAlias, scope));
    }

    private AgentPublishDependencyVO dependencyNode(String kind, Long resourceId, String name,
                                                    Integer versionNo, String displayAlias, String scope) {
        AgentPublishDependencyVO node = new AgentPublishDependencyVO();
        node.setResourceKind(kind);
        node.setResourceId(ConfigRules.text(resourceId));
        node.setResourceName(name);
        node.setVersionNo(versionNo);
        node.setDisplayAlias(displayAlias);
        node.setScope(scope);
        return node;
    }

    private void insertWithGeneratedCode(AgentPO agent) {
        String prefix = ConfigCodeGenerator.agentPrefix(agent.getAgentType());
        for (int attempt = 0; attempt < MAX_CODE_RETRIES; attempt++) {
            String latestCode = agents.selectMaxCodeByPrefix(prefix);
            agent.setAgentCode(ConfigCodeGenerator.nextCode(prefix, latestCode, attempt));
            try {
                agents.insert(agent);
                return;
            } catch (DuplicateKeyException duplicated) {
                log.warn("自动生成Agent编码发生并发碰撞，准备递增重试，code={}", agent.getAgentCode());
            }
        }
        throw new BusinessException(ApiCode.CONFLICT, "生成 Agent 编码失败，请重试");
    }

    private long insertVersion(long agentId, int versionNo, ConfigReadiness readiness,
                               String description, PromptSectionsBO sections, Integer maxIters, long actorId) {
        AgentVersionPO version = new AgentVersionPO();
        version.setAgentVersionId(IdWorker.getId());
        version.setAgentId(agentId);
        version.setVersionNo(versionNo);
        version.setReadiness(readiness);
        version.setDescription(description);
        version.setPromptSections(sections);
        version.setSystemPrompt(PromptSectionRules.assemble(sections));
        version.setMaxIters(maxIters);
        version.setCreatorId(actorId);
        version.setLastUpdaterId(actorId);
        versions.insert(version);
        return version.getAgentVersionId();
    }

    private void insertVersionBindings(long versionId, AgentDraftBindingsBO bindings, long actorId) {
        if (!bindings.getToolIds().isEmpty()) {
            List<AgentVersionToolPO> rows = new ArrayList<>();
            int sortNo = 0;
            for (Long toolId : bindings.getToolIds()) {
                AgentVersionToolPO row = new AgentVersionToolPO();
                row.setAgentVersionId(versionId);
                row.setToolId(toolId);
                row.setSortNo(sortNo++);
                row.setCreatorId(actorId);
                row.setLastUpdaterId(actorId);
                rows.add(row);
            }
            versionTools.insertBatch(rows);
        }

        if (!bindings.getDelegations().isEmpty()) {
            for (AgentVersionSubagentPO delegation : bindings.getDelegations()) {
                delegation.setMasterVersionId(versionId);
                delegation.setCreatorId(actorId);
                delegation.setLastUpdaterId(actorId);
            }
            versionSubagents.insertBatch(bindings.getDelegations());
        }
    }

    private List<AgentToolBindingBO> toolCandidates(List<Long> toolIds) {
        if (toolIds.isEmpty()) {
            return List.of();
        }
        List<AgentToolBindingBO> tools = versions.selectToolCandidates(toolIds);
        if (tools.size() != toolIds.size()) {
            throw new BusinessException(ApiCode.BAD_REQUEST, "部分工具不存在，请刷新后重新选择");
        }
        for (AgentToolBindingBO tool : tools) {
            if (!tool.isUsable()) {
                throw new BusinessException(ApiCode.BAD_REQUEST,
                        "工具 " + tool.getToolName() + " 不可关联：" + tool.getUnusableReason());
            }
        }
        return tools;
    }

    private Map<Long, AgentDelegationBO> delegationCandidates(List<Long> subagentIds) {
        if (subagentIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, AgentDelegationBO> candidates = versions.selectDelegationCandidates(subagentIds).stream()
                .collect(Collectors.toMap(AgentDelegationBO::getSubagentId, Function.identity()));
        for (Long subagentId : subagentIds) {
            if (!candidates.containsKey(subagentId)) {
                throw new BusinessException(ApiCode.BAD_REQUEST, "部分子 Agent 不存在，请刷新后重新选择");
            }
        }
        return candidates;
    }

    private void requireDistinctToolNames(List<AgentToolBindingBO> tools) {
        Set<String> names = new HashSet<>();
        for (AgentToolBindingBO tool : tools) {
            if (AskUserTool.NAME.equals(tool.getExposedName())) {
                throw new BusinessException(ApiCode.BAD_REQUEST, AskUserTool.NAME + " 是平台保留的工具名");
            }
            if (tool.getExposedName() != null && !tool.getExposedName().isBlank() && !names.add(tool.getExposedName())) {
                throw new BusinessException(ApiCode.BAD_REQUEST, "模型工具名 " + tool.getExposedName() + " 重复，同一 Agent 不能绑定同名工具");
            }
        }
    }

    private void requireNoActiveUsage(long agentId, String action) {
        List<String> owners = agents.selectUsages(agentId).stream()
                .filter(ConfigUsageBO::isActive)
                .map(usage -> usage.getOwnerName() + "（" + usage.getOwnerCode() + "）")
                .distinct()
                .toList();
        if (!owners.isEmpty()) {
            throw new BusinessException(ApiCode.CONFLICT,
                    "以下启用的 Master 正在委派该子 Agent，无法" + action + "：" + String.join("、", owners));
        }
    }

    private void requireNoAnyUsage(long agentId) {
        List<String> owners = agents.selectUsages(agentId).stream()
                .map(usage -> usage.getOwnerName() + "（" + usage.getOwnerCode() + "）")
                .distinct()
                .toList();
        if (!owners.isEmpty()) {
            throw new BusinessException(ApiCode.CONFLICT,
                    "以下 Master 仍引用该子 Agent，请先调整委派并重新发布后再删除：" + String.join("、", owners));
        }
    }

    private AgentConfigVO summary(long agentId) {
        List<AgentSummaryBO> summaries = agents.selectSummaries(agentId);
        if (summaries.isEmpty()) {
            throw new BusinessException(ApiCode.NOT_FOUND, "Agent 不存在");
        }
        return converter.toView(summaries.get(0));
    }

    private AgentPO requireAgent(long agentId) {
        AgentPO agent = agents.selectOne(Wrappers.<AgentPO>lambdaQuery()
                .eq(AgentPO::getAgentId, agentId)
                .isNull(AgentPO::getDeletedAt));
        if (agent == null) {
            throw new BusinessException(ApiCode.NOT_FOUND, "Agent 不存在");
        }
        return agent;
    }

    private AgentPO requireAgentIncludingDeleted(long agentId) {
        AgentPO agent = agents.selectOne(Wrappers.<AgentPO>lambdaQuery()
                .eq(AgentPO::getAgentId, agentId));
        if (agent == null) {
            throw new BusinessException(ApiCode.NOT_FOUND, "Agent 不存在");
        }
        return agent;
    }

    private AgentVersionPO requireVersion(long agentId, long versionId) {
        AgentVersionPO version = versions.selectOne(Wrappers.<AgentVersionPO>lambdaQuery()
                .eq(AgentVersionPO::getAgentId, agentId)
                .eq(AgentVersionPO::getAgentVersionId, versionId));
        if (version == null) {
            throw new BusinessException(ApiCode.NOT_FOUND, "Agent 配置版本不存在");
        }
        return version;
    }

    private AgentReleasePO requireRelease(long agentId, long releaseId) {
        AgentReleasePO release = releases.selectOne(Wrappers.<AgentReleasePO>lambdaQuery()
                .eq(AgentReleasePO::getMasterAgentId, agentId)
                .eq(AgentReleasePO::getReleaseId, releaseId));
        if (release == null) {
            throw new BusinessException(ApiCode.NOT_FOUND, "Master 发布快照不存在");
        }
        return release;
    }

    private static List<Long> distinctIds(List<String> values, String label) {
        Set<Long> ids = new LinkedHashSet<>();
        for (String value : values) {
            if (!ids.add(ConfigRules.parseId(value, label))) {
                throw new BusinessException(ApiCode.BAD_REQUEST, label + "重复");
            }
        }
        return new ArrayList<>(ids);
    }

    private static String sha256(String text) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 不可用", impossible);
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String trimToEmpty(String value) {
        return value == null ? "" : value.trim();
    }
}
