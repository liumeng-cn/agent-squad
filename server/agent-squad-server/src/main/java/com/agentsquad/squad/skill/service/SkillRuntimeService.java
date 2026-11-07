package com.agentsquad.squad.skill.service;

import com.agentsquad.squad.common.exception.RuntimeBusinessException;
import com.agentsquad.squad.common.exception.RuntimeExceptionCode;
import com.agentsquad.squad.common.util.RuntimePayloads;
import com.agentsquad.squad.conversation.service.ExecutionScopeService;
import com.agentsquad.squad.domain.exception.SkillCapabilityUnavailableException;
import com.agentsquad.squad.domain.model.SkillCatalog;
import com.agentsquad.squad.domain.model.SkillPackageValidator;
import com.agentsquad.squad.domain.entity.SkillReferenceDO;
import com.agentsquad.squad.domain.entity.SkillVersionDO;
import com.agentsquad.squad.domain.entity.SkillTextFileDO;
import com.agentsquad.squad.skill.repository.SkillRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

/**
 * 固定执行所用的技能版本并读取技能内容
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
            List<SkillVersionDO> versions = repository.listSelectable(userId);
            if (versions.size() > 64) {
                throw new RuntimeBusinessException(RuntimeExceptionCode.CONFLICT, "可用技能超过 64 项，请停用不需要的技能后重试");
            }
            repository.insertCatalog(userId, scopeId);
            for (SkillVersionDO version : versions) {
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
    public SkillTextFileDO read(String userId, String scopeId, String versionId, String path) {
        scopes.lockOwned(userId, scopeId, false);
        scopes.requireReadable(userId, scopeId);
        SkillReferenceDO reference = requireReference(userId, scopeId, versionId);
        validateReferences(userId, List.of(reference));
        SkillTextFileDO file = repository.file(userId, versionId, SkillPackageValidator.validatePath(path));
        if (file == null) {
            throw new RuntimeBusinessException(RuntimeExceptionCode.NOT_FOUND, "技能文件不存在");
        }
        return file;
    }

    @Transactional
    public List<SkillTextFileDO> files(String userId, String scopeId, String versionId) {
        scopes.lockOwned(userId, scopeId, false);
        scopes.requireReadable(userId, scopeId);
        SkillReferenceDO reference = requireReference(userId, scopeId, versionId);
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
        SkillReferenceDO reference = requireReference(userId, scopeId, versionId);
        validateReferences(userId, List.of(reference));
        SkillVersionDO row = repository.find(userId, versionId);
        List<String> required;
        try {
            required = RuntimePayloads.readJson(objectMapper, row.requiredToolsJson()).valueStream()
                    .map(com.fasterxml.jackson.databind.JsonNode::asText).toList();
        } catch (java.io.IOException exception) {
            throw new IllegalStateException("技能工具依赖记录损坏", exception);
        }
        if (allowedTools == null || !allowedTools.containsAll(required)) {
            throw new SkillCapabilityUnavailableException();
        }
    }

    private SkillReferenceDO requireReference(String userId, String scopeId, String versionId) {
        return repository.references(userId, scopeId).stream()
                .filter(reference -> reference.skillVersionId().equals(versionId)).findFirst()
                .orElseThrow(() -> new RuntimeBusinessException(RuntimeExceptionCode.FORBIDDEN,
                        "当前执行未获授权使用此技能版本"));
    }

    private void validateReferences(String userId, List<SkillReferenceDO> references) {
        for (SkillReferenceDO reference : references) {
            SkillVersionDO current = repository.lockVersion(userId, reference.skillVersionId());
            if (current == null || "REVOKED".equals(current.status())
                    || current.authorizationRevision() != reference.authorizationRevision()
                    || !current.contentHash().equals(reference.contentHash())) {
                throw new RuntimeBusinessException(RuntimeExceptionCode.CONFLICT,
                        "SKILL_REVOKED：执行引用的技能已撤销或内容失效，请重新发起执行");
            }
        }
    }
}
