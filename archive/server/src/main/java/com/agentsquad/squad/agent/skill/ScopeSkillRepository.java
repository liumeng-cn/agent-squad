package com.agentsquad.squad.agent.skill;

import com.agentsquad.squad.agent.RuntimeContextIds;

import com.agentsquad.squad.model.bo.SkillSelectionBO;

import com.agentsquad.squad.service.ExecutionScopeService;
import com.agentsquad.squad.model.bo.SkillCatalogBO;
import com.agentsquad.squad.model.bo.SkillReferenceBO;
import com.agentsquad.squad.service.SkillRuntimeService;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.skill.AgentSkill;
import io.agentscope.core.skill.repository.AgentSkillRepositoryInfo;
import io.agentscope.core.skill.repository.RuntimeContextSkillRepository;
import io.agentscope.core.skill.util.MarkdownSkillParser;
import io.agentscope.harness.agent.skill.LazyResourceCapable;
import io.agentscope.harness.agent.skill.SkillResources;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

/**
 * 固定执行技能目录的 AgentScope 只读适配器。
 * <p>
 * 无运行上下文时返回空目录；真实上下文只由 SDK Session 解析归属，文件仅从平台版本库装配，禁止磁盘回退。
 */
@Component
public class ScopeSkillRepository implements RuntimeContextSkillRepository, LazyResourceCapable {

    public static final String SOURCE_PREFIX = "platform-skill:";

    @Resource
    private ExecutionScopeService scopes;

    @Resource
    private SkillRuntimeService skills;

    @Override
    public List<AgentSkill> getAllSkills(RuntimeContext context) {
        SkillSelectionBO selection = context == null ? null : context.get(SkillSelectionBO.class);
        return getSkills(context, selection == null ? null : Set.copyOf(selection.skillVersionIds()));
    }

    /**
     * Worker 可显式传入局部版本集合；选择只做已固定目录的缩小，不允许借此获取其他用户或版本。
     */
    public List<AgentSkill> getSkills(RuntimeContext context, Set<Long> selectedVersionIds) {
        if (context == null || context.getSessionId() == null || context.getSessionId().isBlank()) {
            return List.of();
        }
        Long userId = RuntimeContextIds.userId(context);
        Long scopeId = scopes.scopeForSession(userId, context.getSessionId());
        SkillCatalogBO catalog = skills.freeze(userId, scopeId);
        return catalog.skills().stream()
                .filter(skill -> selectedVersionIds == null || selectedVersionIds.contains(skill.getSkillVersionId()))
                .map(skill -> toFrameworkSkill(userId, scopeId, skill)).toList();
    }

    @Override
    public SkillResources resourcesFor(String skillName, RuntimeContext context) {
        if (context == null || context.getSessionId() == null || context.getSessionId().isBlank()) {
            return SkillResources.empty();
        }
        Long scopeId = scopes.scopeForSession(RuntimeContextIds.userId(context), context.getSessionId());
        SkillSelectionBO selection = context.get(SkillSelectionBO.class);
        ScopeSkillCapabilities capabilities = context.get(ScopeSkillCapabilities.class);
        Set<String> allowedTools = capabilities == null ? Set.of() : capabilities.toolNames();
        SkillCatalogBO catalog = skills.freeze(RuntimeContextIds.userId(context), scopeId);
        return catalog.skills().stream().filter(skill -> skill.getName().equals(skillName))
                .filter(skill -> selection == null || selection.skillVersionIds().contains(skill.getSkillVersionId()))
                .findFirst().<SkillResources>map(skill -> new ScopedSkillResources(skills,
                        RuntimeContextIds.userId(context), scopeId, skill.getSkillVersionId(), allowedTools)).orElseGet(SkillResources::empty);
    }

    @Override
    public AgentSkill getSkill(String name) {
        return null;
    }

    @Override
    public List<String> getAllSkillNames() {
        return List.of();
    }

    @Override
    public List<AgentSkill> getAllSkills() {
        return List.of();
    }

    @Override
    public boolean save(List<AgentSkill> skills, boolean force) {
        throw new UnsupportedOperationException("平台技能目录只允许通过用户导入接口发布");
    }

    @Override
    public boolean delete(String skillName) {
        throw new UnsupportedOperationException("平台技能目录不支持 SDK 删除");
    }

    @Override
    public boolean skillExists(String skillName) {
        return false;
    }

    @Override
    public AgentSkillRepositoryInfo getRepositoryInfo() {
        return new AgentSkillRepositoryInfo("platform", "scope-versioned", false);
    }

    @Override
    public String getSource() {
        return SOURCE_PREFIX;
    }

    @Override
    public void setWriteable(boolean writeable) {
        if (writeable) {
            throw new UnsupportedOperationException("平台技能目录始终只读");
        }
    }

    @Override
    public boolean isWriteable() {
        return false;
    }

    private AgentSkill toFrameworkSkill(Long userId, Long scopeId, SkillReferenceBO skill) {
        String content = skills.read(userId, scopeId, skill.getSkillVersionId(), "SKILL.md").getContent();
        var parsed = MarkdownSkillParser.parse(content);
        return AgentSkill.builder().metadata(parsed.getMetadata()).name(skill.getName()).description(skill.getDescription())
                .skillContent(parsed.getContent()).source(SOURCE_PREFIX + skill.getSkillVersionId())
                .putMetadata("version", skill.getVersion()).putMetadata("contentHash", skill.getContentHash()).build();
    }
}
