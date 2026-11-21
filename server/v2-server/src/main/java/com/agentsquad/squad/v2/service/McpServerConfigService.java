package com.agentsquad.squad.v2.service;

import com.agentsquad.squad.common.enums.ApiCode;
import com.agentsquad.squad.common.exceptions.BusinessException;
import com.agentsquad.squad.common.utils.CurrentUserContext;
import com.agentsquad.squad.v2.converter.McpServerConfigConverter;
import com.agentsquad.squad.v2.dto.McpServerCreateDTO;
import com.agentsquad.squad.v2.dto.McpServerDraftDTO;
import com.agentsquad.squad.v2.enums.AgentConfigStatus;
import com.agentsquad.squad.v2.enums.AgentVersionStatus;
import com.agentsquad.squad.v2.mapper.McpServerMapper;
import com.agentsquad.squad.v2.mapper.McpServerVersionMapper;
import com.agentsquad.squad.v2.po.McpServerPO;
import com.agentsquad.squad.v2.po.McpServerVersionPO;
import com.agentsquad.squad.v2.vo.McpServerVO;
import com.agentsquad.squad.v2.vo.McpServerVersionOptionVO;
import com.agentsquad.squad.v2.vo.McpServerVersionVO;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import jakarta.annotation.Resource;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.util.List;

/**
 * MCP连接配置管理
 * <p>
 * 维护目录与不可变发布版本。
 */
@Service
public class McpServerConfigService {
    @Resource
    private McpServerMapper catalog;

    @Resource
    private McpServerVersionMapper versions;

    @Resource
    private McpServerConfigConverter converter;

    public List<McpServerVersionOptionVO> options() {
        return versions.usableOptions();
    }

    public List<McpServerVO> list() {
        return catalog.selectList(Wrappers.<McpServerPO>lambdaQuery().orderByAsc(McpServerPO::getServerCode))
                .stream().map(converter::toView).toList();
    }

    public McpServerVO create(McpServerCreateDTO request) {
        var entry = new McpServerPO();
        entry.setMcpServerId(IdWorker.getId());
        entry.setServerCode(request.getServerCode());
        entry.setServerName(request.getServerName());
        entry.setStatus(AgentConfigStatus.ENABLED);
        entry.setCreatorId(actor());
        entry.setLastUpdaterId(entry.getCreatorId());

        try {
            catalog.insert(entry);
        } catch (DuplicateKeyException conflict) {
            throw new BusinessException(ApiCode.CONFLICT, "MCP连接编码已存在");
        }
        return converter.toView(entry);
    }

    public List<McpServerVersionVO> listVersions(long resourceId) {
        requireEntry(resourceId);
        return versions.selectList(Wrappers.<McpServerVersionPO>lambdaQuery()
                        .eq(McpServerVersionPO::getMcpServerId, resourceId)
                        .orderByDesc(McpServerVersionPO::getVersionNo))
                .stream().map(converter::toView).toList();
    }

    /**
     * 校验连接版本
     * <p>
     * 工具只能绑定启用目录下的已发布连接版本。
     */
    void requireUsableVersion(long versionId) {
        McpServerVersionPO version = versions.selectOne(Wrappers.<McpServerVersionPO>lambdaQuery()
                .eq(McpServerVersionPO::getMcpServerVersionId, versionId)
                .eq(McpServerVersionPO::getStatus, AgentVersionStatus.PUBLISHED));
        if (version == null || requireEntry(version.getMcpServerId()).getStatus() != AgentConfigStatus.ENABLED) {
            throw new BusinessException(ApiCode.BAD_REQUEST, "MCP 连接版本未发布或已停用");
        }
    }

    /**
     * 保存版本草稿
     * <p>
     * 编辑只更新匹配修订号的草稿。已发布版本保留原配置。
     */
    @Transactional
    public McpServerVersionVO saveDraft(long resourceId, Long versionId, McpServerDraftDTO request) {
        requireEntry(resourceId);
        validateEndpoint(request.getEndpoint());

        var draft = new McpServerVersionPO();
        draft.setMcpServerId(resourceId);
        draft.setTransportType(request.getTransportType());
        draft.setEndpoint(request.getEndpoint());
        draft.setCredentialRef(request.getCredentialRef());
        draft.setLastUpdaterId(actor());

        if (versionId == null) {
            insertDraft(draft);
        } else {
            if (request.getRevision() == null) {
                throw new BusinessException(ApiCode.BAD_REQUEST, "编辑草稿需要修订号");
            }
            draft.setMcpServerVersionId(versionId);
            draft.setRevision(request.getRevision());
            requireChanged(versions.updateDraft(draft));
        }
        return converter.toView(requireVersion(resourceId, draft.getMcpServerVersionId()));
    }

    /**
     * 发布资源版本
     * <p>
     * 发布后该版本可被引用，保留此前版本供现有绑定继续使用。
     */
    @Transactional
    public McpServerVersionVO publish(long resourceId, long versionId, long revision) {
        McpServerPO entry = requireEntry(resourceId);
        if (entry.getStatus() != AgentConfigStatus.ENABLED) {
            throw new BusinessException(ApiCode.CONFLICT, "停用的MCP连接不可发布");
        }

        McpServerVersionPO version = requireVersion(resourceId, versionId);
        if (version.getStatus() != AgentVersionStatus.DRAFT || version.getRevision() != revision) {
            throw new BusinessException(ApiCode.CONFLICT, "版本状态或修订号已变化，请刷新");
        }
        version.setRevision(revision);
        version.setLastUpdaterId(actor());
        requireChanged(versions.publish(version));
        return converter.toView(requireVersion(resourceId, versionId));
    }

    private void insertDraft(McpServerVersionPO draft) {
        draft.setMcpServerVersionId(IdWorker.getId());
        draft.setVersionNo(versions.nextVersionNo(draft.getMcpServerId()));
        draft.setStatus(AgentVersionStatus.DRAFT);
        draft.setRevision(0L);
        draft.setCreatorId(draft.getLastUpdaterId());

        try {
            versions.insert(draft);
        } catch (DuplicateKeyException conflict) {
            throw new BusinessException(ApiCode.CONFLICT, "有其他操作创建了新版本，请重试");
        }
    }

    private void validateEndpoint(String endpoint) {
        URI uri;
        try {
            uri = URI.create(endpoint);
        } catch (IllegalArgumentException invalid) {
            throw new BusinessException(ApiCode.BAD_REQUEST, "MCP 连接地址格式无效");
        }

        String scheme = uri.getScheme();
        if (!("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme))
                || uri.getHost() == null || uri.getRawUserInfo() != null || uri.getRawFragment() != null
                || uri.getPort() == 0 || uri.getPort() > 65535) {
            throw new BusinessException(ApiCode.BAD_REQUEST, "请输入不含用户信息或片段的 HTTP 地址");
        }
    }

    private McpServerPO requireEntry(long resourceId) {
        McpServerPO entry = catalog.selectOne(Wrappers.<McpServerPO>lambdaQuery()
                .eq(McpServerPO::getMcpServerId, resourceId));
        if (entry == null) {
            throw new BusinessException(ApiCode.NOT_FOUND, "MCP连接不存在");
        }
        return entry;
    }

    private McpServerVersionPO requireVersion(long resourceId, long versionId) {
        McpServerVersionPO version = versions.selectOne(Wrappers.<McpServerVersionPO>lambdaQuery()
                .eq(McpServerVersionPO::getMcpServerId, resourceId)
                .eq(McpServerVersionPO::getMcpServerVersionId, versionId));
        if (version == null) {
            throw new BusinessException(ApiCode.NOT_FOUND, "MCP连接版本不存在");
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
