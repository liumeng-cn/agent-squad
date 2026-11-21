package com.agentsquad.squad.v2.service;

import com.agentsquad.squad.common.enums.ApiCode;
import com.agentsquad.squad.common.exceptions.BusinessException;
import com.agentsquad.squad.common.utils.CurrentUserContext;
import com.agentsquad.squad.v2.converter.ToolConfigConverter;
import com.agentsquad.squad.v2.dto.ToolCreateDTO;
import com.agentsquad.squad.v2.dto.ToolDraftDTO;
import com.agentsquad.squad.v2.enums.AgentConfigStatus;
import com.agentsquad.squad.v2.enums.AgentVersionStatus;
import com.agentsquad.squad.v2.enums.ToolSource;
import com.agentsquad.squad.v2.mapper.ToolMapper;
import com.agentsquad.squad.v2.mapper.ToolVersionMapper;
import com.agentsquad.squad.v2.po.ToolPO;
import com.agentsquad.squad.v2.po.ToolVersionPO;
import com.agentsquad.squad.v2.vo.ToolVO;
import com.agentsquad.squad.v2.vo.ToolVersionVO;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import jakarta.annotation.Resource;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


import java.util.List;

/**
 * 工具配置管理
 * <p>
 * 维护目录与不可变发布版本。
 */
@Service
public class ToolConfigService {
    @Resource
    private ToolMapper catalog;

    @Resource
    private ToolVersionMapper versions;

    @Resource
    private ToolConfigConverter converter;

    @Resource
    private McpServerConfigService mcpServers;

    public List<ToolVO> list() {
        return catalog.selectList(Wrappers.<ToolPO>lambdaQuery().orderByAsc(ToolPO::getToolCode))
                .stream().map(converter::toView).toList();
    }

    public ToolVO create(ToolCreateDTO request) {
        var entry = new ToolPO();
        entry.setToolId(IdWorker.getId());
        entry.setToolCode(request.getToolCode());
        entry.setToolName(request.getToolName());
        entry.setToolType(request.getToolType());
        entry.setStatus(AgentConfigStatus.ENABLED);
        entry.setCreatorId(actor());
        entry.setLastUpdaterId(entry.getCreatorId());

        try {
            catalog.insert(entry);
        } catch (DuplicateKeyException conflict) {
            throw new BusinessException(ApiCode.CONFLICT, "工具编码已存在");
        }
        return converter.toView(entry);
    }

    public List<ToolVersionVO> listVersions(long resourceId) {
        requireEntry(resourceId);
        return versions.selectList(Wrappers.<ToolVersionPO>lambdaQuery()
                        .eq(ToolVersionPO::getToolId, resourceId)
                        .orderByDesc(ToolVersionPO::getVersionNo))
                .stream().map(converter::toView).toList();
    }

    public List<ToolVersionVO> options() {
        return versions.usableVersions().stream().map(converter::toView).toList();
    }

    /**
     * 保存版本草稿
     * <p>
     * 编辑只更新匹配修订号的草稿。已发布版本保留原配置。
     */
    @Transactional
    public ToolVersionVO saveDraft(long resourceId, Long versionId, ToolDraftDTO request) {
        ToolPO entry = requireEntry(resourceId);
        Long mcpVersionId = validateImplementation(entry.getToolType(), request);

        var draft = new ToolVersionPO();
        draft.setToolId(resourceId);
        draft.setToolType(entry.getToolType());
        draft.setExposedName(request.getExposedName());
        draft.setImplementationKey(request.getImplementationKey());
        draft.setMcpServerVersionId(mcpVersionId);
        draft.setRemoteToolName(request.getRemoteToolName());
        draft.setLastUpdaterId(actor());

        if (versionId == null) {
            insertDraft(draft);
        } else {
            if (request.getRevision() == null) {
                throw new BusinessException(ApiCode.BAD_REQUEST, "编辑草稿需要修订号");
            }
            draft.setToolVersionId(versionId);
            draft.setRevision(request.getRevision());
            requireChanged(versions.updateDraft(draft));
        }
        return converter.toView(requireVersion(resourceId, draft.getToolVersionId()));
    }

    /**
     * 发布资源版本
     * <p>
     * 发布后该版本可被引用，保留此前版本供现有绑定继续使用。
     */
    @Transactional
    public ToolVersionVO publish(long resourceId, long versionId, long revision) {
        ToolPO entry = requireEntry(resourceId);
        if (entry.getStatus() != AgentConfigStatus.ENABLED) {
            throw new BusinessException(ApiCode.CONFLICT, "停用的工具不可发布");
        }

        ToolVersionPO version = requireVersion(resourceId, versionId);
        if (version.getStatus() != AgentVersionStatus.DRAFT || version.getRevision() != revision) {
            throw new BusinessException(ApiCode.CONFLICT, "版本状态或修订号已变化，请刷新");
        }
        if (version.getMcpServerVersionId() != null) {
            mcpServers.requireUsableVersion(version.getMcpServerVersionId());
        }
        version.setRevision(revision);
        version.setLastUpdaterId(actor());
        requireChanged(versions.publish(version));
        return converter.toView(requireVersion(resourceId, versionId));
    }

    private void insertDraft(ToolVersionPO draft) {
        draft.setToolVersionId(IdWorker.getId());
        draft.setVersionNo(versions.nextVersionNo(draft.getToolId()));
        draft.setStatus(AgentVersionStatus.DRAFT);
        draft.setRevision(0L);
        draft.setCreatorId(draft.getLastUpdaterId());

        try {
            versions.insert(draft);
        } catch (DuplicateKeyException conflict) {
            throw new BusinessException(ApiCode.CONFLICT, "有其他操作创建了新版本，请重试");
        }
    }

    private Long validateImplementation(ToolSource source, ToolDraftDTO request) {
        if (source == ToolSource.MCP) {
            if (request.getImplementationKey() != null || request.getMcpServerVersionId() == null
                    || request.getRemoteToolName() == null || request.getRemoteToolName().isBlank()) {
                throw new BusinessException(ApiCode.BAD_REQUEST, "MCP 工具须填写连接版本及远端工具名");
            }
            long versionId;
            try {
                versionId = Long.parseLong(request.getMcpServerVersionId());
            } catch (NumberFormatException invalid) {
                throw new BusinessException(ApiCode.BAD_REQUEST, "MCP 连接版本编号无效");
            }
            mcpServers.requireUsableVersion(versionId);
            return versionId;
        }

        if (request.getImplementationKey() == null || request.getImplementationKey().isBlank()
                || request.getMcpServerVersionId() != null || request.getRemoteToolName() != null) {
            throw new BusinessException(ApiCode.BAD_REQUEST, "原生和 Java 工具须填写实现标识");
        }
        return null;
    }

    private ToolPO requireEntry(long resourceId) {
        ToolPO entry = catalog.selectOne(Wrappers.<ToolPO>lambdaQuery()
                .eq(ToolPO::getToolId, resourceId));
        if (entry == null) {
            throw new BusinessException(ApiCode.NOT_FOUND, "工具不存在");
        }
        return entry;
    }

    private ToolVersionPO requireVersion(long resourceId, long versionId) {
        ToolVersionPO version = versions.selectOne(Wrappers.<ToolVersionPO>lambdaQuery()
                .eq(ToolVersionPO::getToolId, resourceId)
                .eq(ToolVersionPO::getToolVersionId, versionId));
        if (version == null) {
            throw new BusinessException(ApiCode.NOT_FOUND, "工具版本不存在");
        }
        return version;
    }

    private void requireChanged(int rows) {
        if (rows != 1) {
            throw new BusinessException(ApiCode.CONFLICT, "版本状态或修订号已变化，请刷新");
        }
    }

    private long actor() {
        return Long.parseLong(CurrentUserContext.getUserId());
    }
}
