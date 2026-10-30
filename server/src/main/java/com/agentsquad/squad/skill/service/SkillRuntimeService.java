package com.agentsquad.squad.skill.service;

import com.agentsquad.squad.common.exception.RuntimeBusinessException;
import com.agentsquad.squad.common.exception.RuntimeExceptionCode;
import com.agentsquad.squad.common.util.RuntimePayloads;
import com.agentsquad.squad.conversation.service.ExecutionScopeService;
import com.agentsquad.squad.skill.domain.SkillCatalog;
import com.agentsquad.squad.skill.domain.SkillPackageValidator;
import com.agentsquad.squad.skill.domain.SkillReference;
import com.agentsquad.squad.skill.domain.SkillVersionRow;
import com.agentsquad.squad.skill.domain.SkillTextFile;
import com.agentsquad.squad.skill.repository.SkillRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

/**
 * 执行作用域的技能固定与读取边界。
 * <p>
 * 目录第一次冻结后即使为空也不再变更；每次读取均回权威版本检查撤销代际，普通停用不破坏旧引用。
 */
@Service
public class SkillRuntimeService {

    @Resource
    private SkillRepository repository;

    @Resource
    private ExecutionScopeService scopes;

    @Resource
    private ObjectMapper objectMapper;

    /**
     * 复用会话与执行所有者锁序，使同 Scope 并发首次读取只能固定一个目录，不授予任何工具权限。
     */
    @Transactional
    public SkillCatalog freeze(String userId, String scopeId) {
        scopes.lockOwned(userId, scopeId, true);
        scopes.requireReadable(userId, scopeId);
        if (!repository.catalogExists(userId, scopeId)) {
            List<SkillVersionRow> versions = repository.listSelectable(userId);
            if (versions.size() > 64) {
                throw new RuntimeBusinessException(RuntimeExceptionCode.CONFLICT, "可用技能超过 64 项，请停用不需要的技能后重试");
            }
            repository.insertCatalog(userId, scopeId);
            for (SkillVersionRow version : versions) {
                repository.insertReference(userId, scopeId, version);
            }
        }
        SkillCatalog catalog = new SkillCatalog(scopeId, repository.references(userId, scopeId));
        validateReferences(userId, catalog.skills());
        return catalog;
    }

    /**
     * 模型调用、恢复和交付前可重复检查；不存在目录时不隐式补选新技能。
     */
    @Transactional
    public void validate(String userId, String scopeId) {
        scopes.lockOwned(userId, scopeId, false);
        scopes.requireReadable(userId, scopeId);
        validateReferences(userId, repository.references(userId, scopeId));
    }

    /**
     * 只读取当前 Scope 已固定版本的包内文件；不访问宿主文件系统，不允许名称替代版本身份。
     */
    @Transactional
    public SkillTextFile read(String userId, String scopeId, String versionId, String path) {
        scopes.lockOwned(userId, scopeId, false);
        scopes.requireReadable(userId, scopeId);
        SkillReference reference = requireReference(userId, scopeId, versionId);
        validateReferences(userId, List.of(reference));
        SkillTextFile file = repository.file(userId, versionId, SkillPackageValidator.validatePath(path));
        if (file == null) {
            throw new RuntimeBusinessException(RuntimeExceptionCode.NOT_FOUND, "技能文件不存在");
        }
        return file;
    }

    @Transactional
    public List<SkillTextFile> files(String userId, String scopeId, String versionId) {
        scopes.lockOwned(userId, scopeId, false);
        scopes.requireReadable(userId, scopeId);
        SkillReference reference = requireReference(userId, scopeId, versionId);
        validateReferences(userId, List.of(reference));
        return repository.files(userId, versionId);
    }

    /**
     * 工具依赖只做交集验证；缺少能力不会自动安装，也不会扩大调用方原有工具许可。
     */
    @Transactional
    public void requireCapabilities(String userId, String scopeId, String versionId, Set<String> allowedTools) {
        scopes.lockOwned(userId, scopeId, false);
        scopes.requireReadable(userId, scopeId);
        SkillReference reference = requireReference(userId, scopeId, versionId);
        validateReferences(userId, List.of(reference));
        SkillVersionRow row = repository.find(userId, versionId);
        List<String> required;
        try {
            required = RuntimePayloads.readJson(objectMapper, row.requiredToolsJson()).valueStream()
                    .map(com.fasterxml.jackson.databind.JsonNode::asText).toList();
        } catch (java.io.IOException exception) {
            throw new IllegalStateException("技能工具依赖记录损坏", exception);
        }
        if (allowedTools == null || !allowedTools.containsAll(required)) {
            throw new com.agentsquad.squad.skill.domain.SkillCapabilityUnavailableException();
        }
    }

    private SkillReference requireReference(String userId, String scopeId, String versionId) {
        return repository.references(userId, scopeId).stream()
                .filter(reference -> reference.skillVersionId().equals(versionId)).findFirst()
                .orElseThrow(() -> new RuntimeBusinessException(RuntimeExceptionCode.FORBIDDEN,
                        "当前执行未获授权使用此技能版本"));
    }

    private void validateReferences(String userId, List<SkillReference> references) {
        for (SkillReference reference : references) {
            SkillVersionRow current = repository.lockVersion(userId, reference.skillVersionId());
            if (current == null || "REVOKED".equals(current.status())
                    || current.authorizationRevision() != reference.authorizationRevision()
                    || !current.contentHash().equals(reference.contentHash())) {
                throw new RuntimeBusinessException(RuntimeExceptionCode.CONFLICT,
                        "SKILL_REVOKED：执行引用的技能已撤销或内容失效，请重新发起执行");
            }
        }
    }
}
