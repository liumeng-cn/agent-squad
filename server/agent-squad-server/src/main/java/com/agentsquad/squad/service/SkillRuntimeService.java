package com.agentsquad.squad.service;

import com.agentsquad.squad.exceptions.RuntimeBusinessException;
import com.agentsquad.squad.enums.RuntimeExceptionCode;
import com.agentsquad.squad.utils.RuntimePayloads;
import com.agentsquad.squad.exceptions.SkillCapabilityUnavailableException;
import com.agentsquad.squad.model.bo.SkillCatalogBO;
import com.agentsquad.squad.validator.SkillPackageValidator;
import com.agentsquad.squad.model.bo.SkillReferenceBO;
import com.agentsquad.squad.model.po.SkillVersionPO;
import com.agentsquad.squad.model.po.SkillGrantPO;
import com.agentsquad.squad.model.po.SkillTextFilePO;
import com.agentsquad.squad.mapper.SkillMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.Set;

/**
 * 固定执行所用的技能版本并读取技能内容
 * <p>
 * 目录第一次冻结后即使为空也不再变更；每次读取均回权威版本检查撤销代际，普通停用不破坏旧引用。
 */
@Service
public class SkillRuntimeService {

    @Resource
    private SkillMapper skillMapper;

    @Resource
    private ExecutionScopeService scopes;

    @Resource
    private ObjectMapper objectMapper;

    /**
     * 复用会话与执行所有者锁序，使同 Scope 并发首次读取只能固定一个目录，不授予任何工具权限。
     */
    @Transactional
    public SkillCatalogBO freeze(String userId, String scopeId) {
        scopes.lockOwned(userId, scopeId, true);
        scopes.requireReadable(userId, scopeId);
        if (skillMapper.catalogExists(userId, scopeId).isEmpty()) {
            List<SkillVersionPO> versions = listSelectable(userId);
            if (versions.size() > 64) {
                throw new RuntimeBusinessException(RuntimeExceptionCode.CONFLICT, "可用技能超过 64 项，请停用不需要的技能后重试");
            }
            skillMapper.insertCatalog(UUID.randomUUID().toString(), userId, scopeId);
            for (SkillVersionPO version : versions) {
                skillMapper.insertReference(UUID.randomUUID().toString(), userId, scopeId, version);
            }
        }
        SkillCatalogBO catalog = new SkillCatalogBO(scopeId, skillMapper.references(userId, scopeId));
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
        validateReferences(userId, skillMapper.references(userId, scopeId));
    }

    /**
     * 只读取当前 Scope 已固定版本的包内文件；不访问宿主文件系统，不允许名称替代版本身份。
     */
    @Transactional
    public SkillTextFilePO read(String userId, String scopeId, String versionId, String path) {
        scopes.lockOwned(userId, scopeId, false);
        scopes.requireReadable(userId, scopeId);
        SkillReferenceBO reference = requireReference(userId, scopeId, versionId);
        validateReferences(userId, List.of(reference));
        SkillTextFilePO file = skillMapper.file(userId, versionId, SkillPackageValidator.validatePath(path));
        if (file == null) {
            throw new RuntimeBusinessException(RuntimeExceptionCode.NOT_FOUND, "技能文件不存在");
        }
        return file;
    }

    @Transactional
    public List<SkillTextFilePO> files(String userId, String scopeId, String versionId) {
        scopes.lockOwned(userId, scopeId, false);
        scopes.requireReadable(userId, scopeId);
        SkillReferenceBO reference = requireReference(userId, scopeId, versionId);
        validateReferences(userId, List.of(reference));
        return skillMapper.files(userId, versionId);
    }

    /**
     * 工具依赖只做交集验证；缺少能力不会自动安装，也不会扩大调用方原有工具许可。
     */
    @Transactional
    public void requireCapabilities(String userId, String scopeId, String versionId, Set<String> allowedTools) {
        scopes.lockOwned(userId, scopeId, false);
        scopes.requireReadable(userId, scopeId);
        SkillReferenceBO reference = requireReference(userId, scopeId, versionId);
        validateReferences(userId, List.of(reference));
        SkillVersionPO row = skillMapper.find(userId, versionId);
        List<String> required;
        try {
            required = RuntimePayloads.readJson(objectMapper, row.getRequiredToolsJson()).valueStream()
                    .map(com.fasterxml.jackson.databind.JsonNode::asText).toList();
        } catch (java.io.IOException exception) {
            throw new IllegalStateException("技能工具依赖记录损坏", exception);
        }
        if (allowedTools == null || !allowedTools.containsAll(required)) {
            throw new SkillCapabilityUnavailableException();
        }
    }

    private SkillReferenceBO requireReference(String userId, String scopeId, String versionId) {
        return skillMapper.references(userId, scopeId).stream()
                .filter(reference -> reference.getSkillVersionId().equals(versionId)).findFirst()
                .orElseThrow(() -> new RuntimeBusinessException(RuntimeExceptionCode.FORBIDDEN,
                        "当前执行未获授权使用此技能版本"));
    }

    private void validateReferences(String userId, List<SkillReferenceBO> references) {
        for (SkillReferenceBO reference : references) {
            SkillVersionPO current = skillMapper.lockVersion(userId, reference.getSkillVersionId());
            if (current == null || "REVOKED".equals(current.getStatus())
                    || current.getAuthorizationRevision() != reference.getAuthorizationRevision()
                    || !current.getContentHash().equals(reference.getContentHash())) {
                throw new RuntimeBusinessException(RuntimeExceptionCode.CONFLICT,
                        "SKILL_REVOKED：执行引用的技能已撤销或内容失效，请重新发起执行");
            }
        }
    }
    /**
     * 每个名称只选择最新启用版本；旧执行的引用由独立固定表保存。
     */
    private List<SkillVersionPO> listSelectable(String userId) {
        // 先按稳定顺序锁版本，再锁各版本授权；不使用带快照子查询的 JOIN 推断当前启用版本。
        List<SkillVersionPO> versions = skillMapper.lockVersions(userId);
        Map<String, SkillVersionPO> selected = new LinkedHashMap<>();
        for (SkillVersionPO version : versions) {
            if (!"ENABLED".equals(version.getStatus())) {
                continue;
            }
            SkillGrantPO grant = skillMapper.lockGrant(userId, version.getSkillVersionId());
            if (grant != null && grant.isEnabled()) {
                selected.put(version.getName(), version);
            }
        }
        return selected.values().stream().sorted(java.util.Comparator.comparing(SkillVersionPO::getName)).toList();
    }

}
