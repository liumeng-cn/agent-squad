package com.agentsquad.squad.integration.agentscope;

import com.agentsquad.squad.skill.integration.ScopeSkillRepository;
import com.agentsquad.squad.skill.integration.SkillSelection;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.skill.AgentSkill;
import io.agentscope.harness.agent.skill.curator.SkillVisibilityFilter;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 平台技能发布视图的最终过滤。
 * <p>
 * 排除框架自动发现的工作目录技能，Worker仅保留服务端局部快照选中的版本。
 */
@Component
public class PlatformSkillVisibility implements SkillVisibilityFilter {
    @Override
    public List<AgentSkill> filter(List<AgentSkill> candidates, RuntimeContext context) {
        SkillSelection selected = context == null ? null : context.get(SkillSelection.class);
        return candidates.stream()
                .filter(skill -> skill.getSource().startsWith(ScopeSkillRepository.SOURCE_PREFIX))
                .filter(skill -> selected == null || selected.skillVersionIds().contains(
                        skill.getSource().substring(ScopeSkillRepository.SOURCE_PREFIX.length())))
                .toList();
    }
}
