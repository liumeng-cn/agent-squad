package com.agentsquad.squad.v2.service;

import com.agentsquad.squad.common.enums.ApiCode;
import com.agentsquad.squad.common.exceptions.BusinessException;
import com.agentsquad.squad.common.utils.CurrentUserContext;
import com.agentsquad.squad.v2.converter.AgentConfigConverter;
import com.agentsquad.squad.v2.dto.AgentCreateDTO;
import com.agentsquad.squad.v2.dto.AgentDelegationDTO;
import com.agentsquad.squad.v2.dto.AgentDraftDTO;
import com.agentsquad.squad.v2.enums.AgentConfigStatus;
import com.agentsquad.squad.v2.enums.AgentType;
import com.agentsquad.squad.v2.enums.AgentVersionStatus;
import com.agentsquad.squad.v2.mapper.AgentMapper;
import com.agentsquad.squad.v2.mapper.AgentVersionMapper;
import com.agentsquad.squad.v2.mapper.AgentVersionSubagentMapper;
import com.agentsquad.squad.v2.mapper.AgentVersionToolMapper;
import com.agentsquad.squad.v2.po.AgentPO;
import com.agentsquad.squad.v2.po.AgentVersionPO;
import com.agentsquad.squad.v2.po.AgentVersionSubagentPO;
import com.agentsquad.squad.v2.po.AgentVersionToolPO;
import com.agentsquad.squad.v2.vo.AgentConfigVO;
import com.agentsquad.squad.v2.vo.AgentConfigVersionVO;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import jakarta.annotation.Resource;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;

/**
 * Agent配置管理
 * <p>
 * 管理目录、草稿及版本发布，已发布的配置和绑定供运行时按固定版本读取。
 */
@Service
public class AgentConfigService {
    @Resource
    private AgentMapper agents;

    @Resource
    private AgentVersionMapper versions;

    @Resource
    private AgentVersionToolMapper tools;

    @Resource
    private AgentVersionSubagentMapper subagents;

    @Resource
    private AgentConfigConverter converter;

    public List<AgentConfigVO> list() {
        return agents.selectList(Wrappers.<AgentPO>lambdaQuery().orderByAsc(AgentPO::getAgentCode))
                .stream().map(converter::toView).toList();
    }

    public AgentConfigVO create(AgentCreateDTO request) {
        var agent = new AgentPO();
        agent.setAgentId(IdWorker.getId());
        agent.setAgentCode(request.getAgentCode());
        agent.setAgentName(request.getAgentName());
        agent.setAgentType(request.getAgentType());
        agent.setStatus(AgentConfigStatus.ENABLED);
        agent.setRevision(0L);
        agent.setCreatorId(actor());
        agent.setLastUpdaterId(agent.getCreatorId());

        try {
            agents.insert(agent);
        } catch (DuplicateKeyException conflict) {
            throw new BusinessException(ApiCode.CONFLICT, "Agent 编码已存在");
        }
        return converter.toView(agent);
    }

    public List<AgentConfigVersionVO> listVersions(long agentId) {
        requireAgent(agentId);
        return versions.selectList(Wrappers.<AgentVersionPO>lambdaQuery()
                        .eq(AgentVersionPO::getAgentId, agentId)
                        .orderByDesc(AgentVersionPO::getVersionNo))
                .stream().map(converter::toView).toList();
    }

    public AgentConfigVersionVO readVersion(long agentId, long versionId) {
        return view(requireVersion(agentId, versionId));
    }

    /**
     * 保存配置草稿
     * <p>
     * 编辑先条件推进修订号，再替换关联；任一步失败都会回滚。
     */
    @Transactional
    public AgentConfigVersionVO saveDraft(long agentId, Long versionId, AgentDraftDTO request) {
        AgentPO agent = requireAgent(agentId);
        List<Long> toolIds = request.getToolVersionIds().stream().map(this::parseId).toList();
        validateTools(toolIds);
        validateDelegations(agent, request.getSubagents());

        AgentVersionPO draft = new AgentVersionPO();
        draft.setAgentId(agentId);
        draft.setSystemPrompt(request.getSystemPrompt());
        draft.setDescription(Objects.requireNonNullElse(request.getDescription(), ""));
        draft.setMaxIters(request.getMaxIters());
        draft.setLastUpdaterId(actor());

        if (versionId == null) {
            insertDraft(draft);
        } else {
            if (request.getRevision() == null) {
                throw new BusinessException(ApiCode.BAD_REQUEST, "编辑草稿需要修订号");
            }
            draft.setAgentVersionId(versionId);
            draft.setRevision(request.getRevision());
            requireChanged(versions.updateDraft(draft));
            tools.delete(Wrappers.<AgentVersionToolPO>lambdaQuery().eq(AgentVersionToolPO::getAgentVersionId, versionId));
            subagents.delete(Wrappers.<AgentVersionSubagentPO>lambdaQuery().eq(AgentVersionSubagentPO::getMasterVersionId, versionId));
        }

        insertBindings(draft.getAgentVersionId(), toolIds, request.getSubagents(), draft.getLastUpdaterId());
        return readVersion(agentId, draft.getAgentVersionId());
    }

    /**
     * 发布配置版本
     * <p>
     * 校验草稿及依赖后冻结版本，并在同一事务切换当前版本。
     */
    @Transactional
    public AgentConfigVO publish(long agentId, long versionId, long revision) {
        AgentPO agent = requireAgent(agentId);
        AgentConfigVersionVO draft = readVersion(agentId, versionId);
        validateTools(draft.getToolVersionIds().stream().map(this::parseId).toList());
        for (var binding : draft.getSubagents()) {
            requirePublishedSubagent(parseId(binding.getSubagentVersionId()));
        }

        AgentVersionPO expected = new AgentVersionPO();
        expected.setAgentId(agentId);
        expected.setAgentVersionId(versionId);
        expected.setRevision(revision);
        expected.setLastUpdaterId(actor());
        requireChanged(versions.publish(expected));

        return switchCurrent(agent, versionId, agent.getRevision());
    }

    /**
     * 切换生效版本
     * <p>
     * 只允许切换到本 Agent 已发布的版本。
     */
    @Transactional
    public AgentConfigVO activate(long agentId, long versionId, long revision) {
        AgentPO agent = requireAgent(agentId);
        AgentVersionPO version = requireVersion(agentId, versionId);
        if (version.getStatus() != AgentVersionStatus.PUBLISHED) {
            throw new BusinessException(ApiCode.CONFLICT, "只能切换到已发布版本");
        }
        return switchCurrent(agent, versionId, revision);
    }

    private void insertDraft(AgentVersionPO draft) {
        draft.setAgentVersionId(IdWorker.getId());
        draft.setVersionNo(versions.nextVersionNo(draft.getAgentId()));
        draft.setStatus(AgentVersionStatus.DRAFT);
        draft.setRevision(0L);
        draft.setCreatorId(draft.getLastUpdaterId());
        try {
            versions.insert(draft);
        } catch (DuplicateKeyException conflict) {
            throw new BusinessException(ApiCode.CONFLICT, "有其他操作创建了新版本，请重试");
        }
    }

    private void insertBindings(long versionId, List<Long> toolIds, List<AgentDelegationDTO> bindings, long actorId) {
        for (Long toolId : toolIds) {
            var link = new AgentVersionToolPO();
            link.setAgentVersionId(versionId);
            link.setToolVersionId(toolId);
            link.setCreatorId(actorId);
            link.setLastUpdaterId(actorId);
            tools.insert(link);
        }
        for (var binding : bindings) {
            var link = new AgentVersionSubagentPO();
            link.setMasterVersionId(versionId);
            link.setSubagentVersionId(parseId(binding.getSubagentVersionId()));
            link.setDelegationName(binding.getDelegationName());
            link.setDelegationDescription(binding.getDelegationDescription());
            link.setCreatorId(actorId);
            link.setLastUpdaterId(actorId);
            subagents.insert(link);
        }
    }

    private void validateTools(List<Long> toolIds) {
        if (toolIds.isEmpty()) {
            return;
        }
        List<String> names = versions.enabledToolNames(toolIds);
        if (names.size() != toolIds.size()) {
            throw new BusinessException(ApiCode.BAD_REQUEST, "工具版本重复、未发布或已停用");
        }
        if (new HashSet<>(names).size() != names.size()) {
            throw new BusinessException(ApiCode.BAD_REQUEST, "同一 Agent 的工具名称不能重复");
        }
    }

    private void validateDelegations(AgentPO master, List<AgentDelegationDTO> bindings) {
        if (!bindings.isEmpty() && master.getAgentType() != AgentType.MASTER) {
            throw new BusinessException(ApiCode.BAD_REQUEST, "只有主 Agent 可以配置委派");
        }

        var names = new HashSet<String>();
        for (var binding : bindings) {
            if (!names.add(binding.getDelegationName())) {
                throw new BusinessException(ApiCode.BAD_REQUEST, "委派名称不能重复");
            }
            requirePublishedSubagent(parseId(binding.getSubagentVersionId()));
        }
    }

    private void requirePublishedSubagent(long versionId) {
        AgentVersionPO version = versions.selectOne(Wrappers.<AgentVersionPO>lambdaQuery()
                .eq(AgentVersionPO::getAgentVersionId, versionId));
        if (version == null || version.getStatus() != AgentVersionStatus.PUBLISHED) {
            throw new BusinessException(ApiCode.BAD_REQUEST, "只能绑定已发布的子 Agent 版本");
        }
        AgentPO child = requireAgent(version.getAgentId());
        if (child.getAgentType() != AgentType.SUB_AGENT || child.getStatus() != AgentConfigStatus.ENABLED) {
            throw new BusinessException(ApiCode.BAD_REQUEST, "委派目标须为启用的子 Agent");
        }
    }

    private AgentConfigVO switchCurrent(AgentPO agent, long versionId, long revision) {
        agent.setCurrentVersionId(versionId);
        agent.setRevision(revision);
        agent.setLastUpdaterId(actor());
        requireChanged(agents.switchVersion(agent));
        agent.setRevision(revision + 1);
        return converter.toView(agent);
    }

    private AgentConfigVersionVO view(AgentVersionPO version) {
        AgentConfigVersionVO view = converter.toView(version);
        view.setToolVersionIds(tools.selectList(Wrappers.<AgentVersionToolPO>lambdaQuery()
                        .eq(AgentVersionToolPO::getAgentVersionId, version.getAgentVersionId())
                        .orderByAsc(AgentVersionToolPO::getId))
                .stream().map(binding -> binding.getToolVersionId().toString()).toList());
        view.setSubagents(subagents.selectList(Wrappers.<AgentVersionSubagentPO>lambdaQuery()
                        .eq(AgentVersionSubagentPO::getMasterVersionId, version.getAgentVersionId())
                        .orderByAsc(AgentVersionSubagentPO::getId))
                .stream().map(converter::toView).toList());
        return view;
    }

    private AgentPO requireAgent(long agentId) {
        AgentPO agent = agents.selectOne(Wrappers.<AgentPO>lambdaQuery().eq(AgentPO::getAgentId, agentId));
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
            throw new BusinessException(ApiCode.NOT_FOUND, "Agent 版本不存在");
        }
        return version;
    }

    private void requireChanged(int changedRows) {
        if (changedRows != 1) {
            throw new BusinessException(ApiCode.CONFLICT, "状态或修订号已变化，请刷新后重试");
        }
    }

    private long actor() {
        return Long.parseLong(CurrentUserContext.getUserId());
    }

    private long parseId(String value) {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException invalid) {
            throw new BusinessException(ApiCode.BAD_REQUEST, "版本编号无效");
        }
    }
}
