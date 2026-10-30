package com.agentsquad.squad.skill.service;

import com.agentsquad.squad.common.exception.RuntimeBusinessException;
import com.agentsquad.squad.common.exception.RuntimeExceptionCode;
import com.agentsquad.squad.skill.config.SkillPackageLimits;
import com.agentsquad.squad.skill.domain.SkillGrantRow;
import com.agentsquad.squad.skill.domain.SkillPackage;
import com.agentsquad.squad.skill.domain.SkillPackageValidator;
import com.agentsquad.squad.skill.domain.SkillVersionRow;
import com.agentsquad.squad.skill.dto.SkillDetail;
import com.agentsquad.squad.skill.dto.SkillGrantRequest;
import com.agentsquad.squad.skill.dto.SkillGrantView;
import com.agentsquad.squad.skill.dto.SkillImportRequest;
import com.agentsquad.squad.skill.dto.SkillPage;
import com.agentsquad.squad.skill.dto.SkillVersionView;
import com.agentsquad.squad.skill.repository.SkillRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * 用户技能发布与授权应用服务。
 * <p>
 * 以认证用户隔离内容；版本发布原子保存文件与默认停用授权，启用和撤销使用乐观版本控制。
 */
@Service
@Slf4j
public class SkillService {

    @Resource
    private SkillRepository repository;

    @Resource
    private SkillPackageLimits limits;

    @Resource
    private ObjectMapper objectMapper;

    /**
     * 同用户同名称版本且内容相同返回原版本；任何文件落库失败回滚整个包，不自动启用。
     */
    @Transactional
    public SkillVersionView importPackage(String userId, SkillImportRequest request) {
        SkillPackage skill = validate(request);
        SkillVersionRow prior = repository.findNamedVersion(userId, skill.name(), skill.version());
        if (prior != null) {
            return sameVersion(prior, skill);
        }
        String versionId = UUID.randomUUID().toString();
        try {
            repository.insertVersion(userId, versionId, skill.name(), skill.version(), skill.description(),
                    skill.contentHash(), writeTools(skill.requiredTools()));
        } catch (DuplicateKeyException exception) {
            return sameVersion(repository.findNamedVersionCurrent(userId, skill.name(), skill.version()), skill);
        }
        for (var file : skill.files()) {
            repository.insertFile(userId, versionId, file);
        }
        repository.insertGrant(userId, versionId);
        log.info("技能版本发布完成 userId={} skillVersionId={} fileCount={} enabled=false",
                userId, versionId, skill.files().size());
        return versionView(requireOwned(userId, versionId));
    }

    public SkillDetail detail(String userId, String versionId) {
        SkillVersionRow row = requireOwned(userId, versionId);
        SkillGrantRow grant = repository.grant(userId, versionId);
        return new SkillDetail(versionView(row), repository.files(userId, versionId).stream().map(file -> new com.agentsquad.squad.skill.dto.SkillFile(file.path(), file.content())).toList(), grantView(grant));
    }

    public SkillPage list(String userId, String cursor, int limit) {
        int pageSize = Math.min(Math.max(limit, 1), 100);
        List<SkillVersionRow> rows = repository.list(userId, parseCursor(cursor), pageSize + 1);
        boolean hasMore = rows.size() > pageSize;
        List<SkillVersionRow> page = rows.stream().limit(pageSize).toList();
        return new SkillPage(page.stream().map(this::versionView).toList(),
                hasMore ? Long.toString(page.get(page.size() - 1).id()) : null);
    }

    /**
     * 普通启停只影响新执行目录，不递增安全撤销代际，已固定的版本继续可读。
     */
    @Transactional
    public SkillGrantView changeGrant(String userId, String versionId, SkillGrantRequest request) {
        SkillVersionRow row = requireLocked(userId, versionId);
        if (request == null || request.enabled() == null || request.expectedVersion() == null || request.expectedVersion() < 0) {
            throw invalid("授权版本无效");
        }
        if ("REVOKED".equals(row.status())) {
            throw conflict("技能版本已安全撤销，不能重新启用");
        }
        if (repository.changeGrant(userId, versionId, request.expectedVersion(), request.enabled()) != 1) {
            throw conflict("技能授权已变化，请刷新后重试");
        }
        log.info("技能使用授权已更新 userId={} skillVersionId={} enabled={}", userId, versionId, request.enabled());
        return grantView(repository.grant(userId, versionId));
    }

    /**
     * 安全撤销与授权版本更新同事务提交；任何后续在途读取必须核对新代际，不允许恢复启用。
     */
    @Transactional
    public SkillDetail revoke(String userId, String versionId, long expectedVersion) {
        SkillVersionRow row = requireLocked(userId, versionId);
        if (expectedVersion < 0) {
            throw invalid("授权版本无效");
        }
        if ("REVOKED".equals(row.status())) {
            return detail(userId, versionId);
        }
        if (repository.changeGrant(userId, versionId, expectedVersion, false) != 1) {
            throw conflict("技能授权已变化，请刷新后重试");
        }
        repository.revoke(userId, versionId);
        log.info("技能版本已安全撤销 userId={} skillVersionId={}", userId, versionId);
        return detail(userId, versionId);
    }

    public SkillPackage validate(SkillImportRequest request) {
        return SkillPackageValidator.validate(request == null || request.files() == null ? null : request.files().stream().map(file -> file == null ? null : new com.agentsquad.squad.skill.domain.SkillTextFile(file.path(), file.content())).toList(),
                Math.min(Math.max(limits.getMaxFiles(), 1), 64),
                Math.min(Math.max(limits.getMaxFileBytes(), 1), 262144),
                Math.min(Math.max(limits.getMaxPackageBytes(), 1), 16777216));
    }

    private SkillVersionView sameVersion(SkillVersionRow prior, SkillPackage skill) {
        if (prior == null || !prior.contentHash().equals(skill.contentHash())) {
            throw conflict("同名同版本技能已存在且内容不同，请使用新版本号");
        }
        return versionView(prior);
    }

    private SkillVersionRow requireLocked(String userId, String versionId) {
        SkillVersionRow row = repository.lockVersion(userId, versionId);
        if (row == null) {
            throw new RuntimeBusinessException(RuntimeExceptionCode.NOT_FOUND, "技能版本不存在或无权访问");
        }
        return row;
    }

    private SkillVersionRow requireOwned(String userId, String versionId) {
        SkillVersionRow row = repository.find(userId, versionId);
        if (row == null) {
            throw new RuntimeBusinessException(RuntimeExceptionCode.NOT_FOUND, "技能版本不存在或无权访问");
        }
        return row;
    }

    private SkillVersionView versionView(SkillVersionRow row) {
        SkillGrantRow grant = repository.grant(row.userId(), row.skillVersionId());
        String status = "REVOKED".equals(row.status()) ? "REVOKED"
                : grant != null && grant.enabled() ? "ENABLED" : "DISABLED";
        return new SkillVersionView(row.skillVersionId(), row.name(), row.version(), row.contentHash(),
                row.description(), status);
    }

    private SkillGrantView grantView(SkillGrantRow grant) {
        if (grant == null) {
            throw new IllegalStateException("技能授权记录缺失");
        }
        return new SkillGrantView(grant.skillVersionId(), grant.enabled(), grant.version());
    }

    private String writeTools(List<String> tools) {
        try {
            return objectMapper.writeValueAsString(tools);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("技能工具依赖无法序列化", exception);
        }
    }

    private long parseCursor(String cursor) {
        if (cursor == null || cursor.isBlank()) {
            return 0;
        }
        try {
            long value = Long.parseLong(cursor);
            if (value > 0) {
                return value;
            }
        } catch (NumberFormatException exception) {
            throw invalid("分页游标无效");
        }
        throw invalid("分页游标无效");
    }

    private RuntimeBusinessException invalid(String message) {
        return new RuntimeBusinessException(RuntimeExceptionCode.VALIDATION, message);
    }

    private RuntimeBusinessException conflict(String message) {
        return new RuntimeBusinessException(RuntimeExceptionCode.CONFLICT, message);
    }
}
