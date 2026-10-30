package com.agentsquad.squad.skill.integration;

import com.agentsquad.squad.conversation.service.ExecutionScopeService;
import com.agentsquad.squad.skill.domain.SkillCatalog;
import com.agentsquad.squad.skill.domain.SkillReference;
import com.agentsquad.squad.skill.service.SkillRuntimeService;
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
        SkillSelection selection = context == null ? null : context.get(SkillSelection.class);
        return getSkills(context, selection == null ? null : Set.copyOf(selection.skillVersionIds()));
    }

    /**
     * Worker 可显式传入局部版本集合；选择只做已固定目录的缩小，不允许借此获取其他用户或版本。
     */
    public List<AgentSkill> getSkills(RuntimeContext context, Set<String> selectedVersionIds) {
        if (context == null || context.getUserId() == null || context.getUserId().isBlank()
                || context.getSessionId() == null || context.getSessionId().isBlank()) {
            return List.of();
        }
        String userId = context.getUserId();
        String scopeId = scopes.scopeForSession(userId, context.getSessionId());
        SkillCatalog catalog = skills.freeze(userId, scopeId);
        return catalog.skills().stream()
                .filter(skill -> selectedVersionIds == null || selectedVersionIds.contains(skill.skillVersionId()))
                .map(skill -> toFrameworkSkill(userId, scopeId, skill)).toList();
    }

    @Override
    public SkillResources resourcesFor(String skillName, RuntimeContext context) {
        if (context == null || context.getUserId() == null || context.getUserId().isBlank()
                || context.getSessionId() == null || context.getSessionId().isBlank()) {
            return SkillResources.empty();
        }
        String scopeId = scopes.scopeForSession(context.getUserId(), context.getSessionId());
        SkillSelection selection = context.get(SkillSelection.class);
        ScopeSkillCapabilities capabilities = context.get(ScopeSkillCapabilities.class);
        Set<String> allowedTools = capabilities == null ? Set.of() : capabilities.toolNames();
        SkillCatalog catalog = skills.freeze(context.getUserId(), scopeId);
        return catalog.skills().stream().filter(skill -> skill.name().equals(skillName))
                .filter(skill -> selection == null || selection.skillVersionIds().contains(skill.skillVersionId()))
                .findFirst().<SkillResources>map(skill -> new ScopedSkillResources(skills,
                        context.getUserId(), scopeId, skill.skillVersionId(), allowedTools)).orElseGet(SkillResources::empty);
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

    private AgentSkill toFrameworkSkill(String userId, String scopeId, SkillReference skill) {
        String content = skills.read(userId, scopeId, skill.skillVersionId(), "SKILL.md").content();
        var parsed = MarkdownSkillParser.parse(content);
        return AgentSkill.builder().metadata(parsed.getMetadata()).name(skill.name()).description(skill.description())
                .skillContent(parsed.getContent()).source(SOURCE_PREFIX + skill.skillVersionId())
                .putMetadata("version", skill.version()).putMetadata("contentHash", skill.contentHash()).build();
    }
}
