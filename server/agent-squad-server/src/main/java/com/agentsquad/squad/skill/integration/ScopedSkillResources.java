package com.agentsquad.squad.skill.integration;

import com.agentsquad.squad.common.exception.RuntimeBusinessException;
import com.agentsquad.squad.common.exception.RuntimeExceptionCode;
import com.agentsquad.squad.skill.domain.SkillTextFile;
import com.agentsquad.squad.skill.service.SkillRuntimeService;
import io.agentscope.harness.agent.skill.SkillResources;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * 单个固定技能版本的按需资源读取器。
 * <p>
 * 每次读取均携带原用户和 Scope 回到授权服务，撤销后旧读取器也不能继续输出正文。
 */
public final class ScopedSkillResources implements SkillResources {

    private final SkillRuntimeService skills;

    private final String userId;

    private final String scopeId;

    private final String versionId;

    private final Set<String> allowedTools;

    public ScopedSkillResources(SkillRuntimeService skills, String userId, String scopeId, String versionId, Set<String> allowedTools) {
        this.skills = skills;
        this.userId = userId;
        this.scopeId = scopeId;
        this.versionId = versionId;
        this.allowedTools = Set.copyOf(allowedTools);
    }

    @Override
    public Optional<String> read(String relativePath) {
        try {
            skills.requireCapabilities(userId, scopeId, versionId, allowedTools);
            return Optional.of(skills.read(userId, scopeId, versionId, relativePath).content());
        } catch (RuntimeBusinessException exception) {
            if (exception.code() == RuntimeExceptionCode.NOT_FOUND) {
                return Optional.empty();
            }
            throw exception;
        }
    }

    @Override
    public Optional<byte[]> readBinary(String relativePath) {
        return read(relativePath).map(value -> value.getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public List<String> list() {
        return skills.files(userId, scopeId, versionId).stream().map(SkillTextFile::path).toList();
    }
}
