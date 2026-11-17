package com.agentsquad.squad.service;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.agentsquad.squad.exceptions.RuntimeBusinessException;
import com.agentsquad.squad.enums.RuntimeExceptionCode;
import com.agentsquad.squad.config.properties.SkillPackageProperties;
import com.agentsquad.squad.converter.SkillFileConverter;
import com.agentsquad.squad.model.po.SkillGrantPO;
import com.agentsquad.squad.model.bo.SkillPackageBO;
import com.agentsquad.squad.validator.SkillPackageValidator;
import com.agentsquad.squad.model.po.SkillTextFilePO;
import com.agentsquad.squad.model.po.SkillVersionPO;
import com.agentsquad.squad.model.vo.SkillDetailVO;
import com.agentsquad.squad.model.dto.SkillGrantDTO;
import com.agentsquad.squad.model.vo.SkillGrantVO;
import com.agentsquad.squad.model.dto.SkillImportDTO;
import com.agentsquad.squad.model.vo.SkillPageVO;
import com.agentsquad.squad.model.vo.SkillVersionVO;
import com.agentsquad.squad.mapper.SkillMapper;
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
    private SkillMapper skillMapper;

    @Resource
    private SkillFileConverter skillFileConverter;

    @Resource
    private SkillPackageProperties limits;

    @Resource
    private ObjectMapper objectMapper;

    /**
     * 同用户同名称版本且内容相同返回原版本；任何文件落库失败回滚整个包，不自动启用。
     */
    @Transactional
    public SkillVersionVO importPackage(Long userId, SkillImportDTO request) {
        SkillPackageBO skill = validate(request);
        SkillVersionPO prior = skillMapper.findNamedVersion(userId, skill.name(), skill.version());
        if (prior != null) {
            return sameVersion(prior, skill);
        }
        Long versionId = IdWorker.getId();
        try {
            skillMapper.insertVersion(userId, versionId, skill.name(), skill.version(), skill.description(),
                    skill.contentHash(), writeTools(skill.requiredTools()));
        } catch (DuplicateKeyException exception) {
            return sameVersion(skillMapper.findNamedVersionCurrent(userId, skill.name(), skill.version()), skill);
        }
        for (var file : skill.files()) {
            skillMapper.insertFile(IdWorker.getId(), userId, versionId, file);
        }
        skillMapper.insertGrant(IdWorker.getId(), userId, versionId);
        log.info("技能版本发布完成 userId={} skillVersionId={} fileCount={} enabled=false",
                userId, versionId, skill.files().size());
        return versionView(requireOwned(userId, versionId));
    }

    public SkillDetailVO detail(Long userId, Long versionId) {
        SkillVersionPO row = requireOwned(userId, versionId);
        SkillGrantPO grant = skillMapper.grant(userId, versionId);
        return new SkillDetailVO(versionView(row),
                skillFileConverter.toViews(skillMapper.files(userId, versionId)), grantView(grant));
    }

    public SkillPageVO list(Long userId, String cursor, int limit) {
        int pageSize = Math.min(Math.max(limit, 1), 100);
        List<SkillVersionPO> rows = skillMapper.list(userId, parseCursor(cursor), pageSize + 1);
        boolean hasMore = rows.size() > pageSize;
        List<SkillVersionPO> page = rows.stream().limit(pageSize).toList();
        return new SkillPageVO(page.stream().map(this::versionView).toList(),
                hasMore ? Long.toString(page.get(page.size() - 1).getId()) : null);
    }

    /**
     * 普通启停只影响新执行目录，不递增安全撤销代际，已固定的版本继续可读。
     */
    @Transactional
    public SkillGrantVO changeGrant(Long userId, Long versionId, SkillGrantDTO request) {
        SkillVersionPO row = requireLocked(userId, versionId);
        if (request == null || request.enabled() == null || request.expectedVersion() == null || request.expectedVersion() < 0) {
            throw invalid("授权版本无效");
        }
        if ("REVOKED".equals(row.getStatus())) {
            throw conflict("技能版本已安全撤销，不能重新启用");
        }
        if (skillMapper.changeGrant(userId, versionId, request.expectedVersion(), request.enabled()) != 1) {
            throw conflict("技能授权已变化，请刷新后重试");
        }
        log.info("技能使用授权已更新 userId={} skillVersionId={} enabled={}", userId, versionId, request.enabled());
        return grantView(skillMapper.grant(userId, versionId));
    }

    /**
     * 安全撤销与授权版本更新同事务提交；任何后续在途读取必须核对新代际，不允许恢复启用。
     */
    @Transactional
    public SkillDetailVO revoke(Long userId, Long versionId, long expectedVersion) {
        SkillVersionPO row = requireLocked(userId, versionId);
        if (expectedVersion < 0) {
            throw invalid("授权版本无效");
        }
        if ("REVOKED".equals(row.getStatus())) {
            return detail(userId, versionId);
        }
        if (skillMapper.changeGrant(userId, versionId, expectedVersion, false) != 1) {
            throw conflict("技能授权已变化，请刷新后重试");
        }
        skillMapper.revoke(userId, versionId);
        log.info("技能版本已安全撤销 userId={} skillVersionId={}", userId, versionId);
        return detail(userId, versionId);
    }

    public SkillPackageBO validate(SkillImportDTO request) {
        return SkillPackageValidator.validate(request == null || request.files() == null ? null : request.files().stream().map(file -> file == null ? null : new SkillTextFilePO(file.path(), file.content())).toList(),
                Math.min(Math.max(limits.getMaxFiles(), 1), 64),
                Math.min(Math.max(limits.getMaxFileBytes(), 1), 262144),
                Math.min(Math.max(limits.getMaxPackageBytes(), 1), 16777216));
    }

    private SkillVersionVO sameVersion(SkillVersionPO prior, SkillPackageBO skill) {
        if (prior == null || !prior.getContentHash().equals(skill.contentHash())) {
            throw conflict("同名同版本技能已存在且内容不同，请使用新版本号");
        }
        return versionView(prior);
    }

    private SkillVersionPO requireLocked(Long userId, Long versionId) {
        SkillVersionPO row = skillMapper.lockVersion(userId, versionId);
        if (row == null) {
            throw new RuntimeBusinessException(RuntimeExceptionCode.NOT_FOUND, "技能版本不存在或无权访问");
        }
        return row;
    }

    private SkillVersionPO requireOwned(Long userId, Long versionId) {
        SkillVersionPO row = skillMapper.find(userId, versionId);
        if (row == null) {
            throw new RuntimeBusinessException(RuntimeExceptionCode.NOT_FOUND, "技能版本不存在或无权访问");
        }
        return row;
    }

    private SkillVersionVO versionView(SkillVersionPO row) {
        SkillGrantPO grant = skillMapper.grant(row.getUserId(), row.getSkillVersionId());
        String status = "REVOKED".equals(row.getStatus()) ? "REVOKED"
                : grant != null && grant.isEnabled() ? "ENABLED" : "DISABLED";
        return new SkillVersionVO(row.getSkillVersionId(), row.getName(), row.getVersion(), row.getContentHash(),
                row.getDescription(), status);
    }

    private SkillGrantVO grantView(SkillGrantPO grant) {
        if (grant == null) {
            throw new IllegalStateException("技能授权记录缺失");
        }
        return new SkillGrantVO(grant.getSkillVersionId(), grant.isEnabled(), grant.getVersion());
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
