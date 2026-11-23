package com.agentsquad.squad.v2.service;

import com.agentsquad.squad.common.enums.ApiCode;
import com.agentsquad.squad.common.exceptions.BusinessException;
import com.agentsquad.squad.v2.agent.mcp.McpClientFactory;
import com.agentsquad.squad.v2.agent.tool.http.HttpToolTemplate;
import com.agentsquad.squad.v2.bo.ConfigUsageBO;
import com.agentsquad.squad.v2.bo.KeyValueBO;
import com.agentsquad.squad.v2.bo.McpConnectionBO;
import com.agentsquad.squad.v2.bo.McpRemoteToolBO;
import com.agentsquad.squad.v2.bo.McpServerSummaryBO;
import com.agentsquad.squad.v2.converter.McpServerConfigConverter;
import com.agentsquad.squad.v2.dto.KeyValueDTO;
import com.agentsquad.squad.v2.dto.McpServerCreateDTO;
import com.agentsquad.squad.v2.dto.McpServerDraftDTO;
import com.agentsquad.squad.v2.dto.McpServerProfileDTO;
import com.agentsquad.squad.v2.enums.ConfigStatus;
import com.agentsquad.squad.v2.enums.VersionStatus;
import com.agentsquad.squad.v2.mapper.McpServerMapper;
import com.agentsquad.squad.v2.mapper.McpServerVersionMapper;
import com.agentsquad.squad.v2.po.McpServerPO;
import com.agentsquad.squad.v2.po.McpServerVersionPO;
import com.agentsquad.squad.v2.utils.ConfigRules;
import com.agentsquad.squad.v2.vo.McpProbeVO;
import com.agentsquad.squad.v2.vo.McpRemoteToolVO;
import com.agentsquad.squad.v2.vo.McpServerDetailVO;
import com.agentsquad.squad.v2.vo.McpServerOptionVO;
import com.agentsquad.squad.v2.vo.McpServerVO;
import com.agentsquad.squad.v2.vo.McpServerVersionVO;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * MCP 服务配置管理
 * <p>
 * 维护远程 MCP 服务的目录、连接版本、启停和连接测试，是工具配置的上游。
 * 工具只能引用启用服务的已发布版本；服务存在生效引用时不能停用。凭据只保存引用名，连接时解析。
 */
@Slf4j
@Service
public class McpServerConfigService {
    private static final String CONFLICT = "服务或版本已被其他操作修改，请刷新后重试";

    private static final Set<String> FORBIDDEN_HEADERS = Set.of("host", "content-length", "connection",
            "transfer-encoding", "upgrade", "expect");

    @Resource
    private McpServerMapper servers;

    @Resource
    private McpServerVersionMapper versions;

    @Resource
    private McpServerConfigConverter converter;

    @Resource
    private CredentialService credentials;

    @Resource
    private McpClientFactory mcpClients;

    /**
     * 查询服务列表
     */
    public List<McpServerVO> list() {
        return servers.selectSummaries(null).stream().map(converter::toView).toList();
    }

    /**
     * 查询服务详情
     * <p>
     * 返回服务摘要、版本记录和引用该服务的工具版本。
     */
    public McpServerDetailVO detail(long mcpServerId) {
        McpServerDetailVO detail = new McpServerDetailVO();
        detail.setServer(summary(mcpServerId));
        detail.setVersions(versions.selectSummaries(mcpServerId).stream().map(converter::toView).toList());
        detail.setUsages(servers.selectUsages(mcpServerId).stream().map(converter::toView).toList());
        return detail;
    }

    /**
     * 新增服务
     * <p>
     * 同一事务创建服务目录和第一个版本草稿；编码重复时返回冲突，整笔回滚。
     */
    @Transactional
    public McpServerDetailVO create(McpServerCreateDTO request) {
        validateDraft(request.getDraft());
        long actorId = ConfigRules.actor();

        McpServerPO server = new McpServerPO();
        server.setMcpServerId(IdWorker.getId());
        server.setServerCode(request.getServerCode());
        server.setServerName(request.getServerName().trim());
        server.setDescription(request.getDescription().trim());
        server.setStatus(ConfigStatus.ENABLED);
        server.setRevision(0L);
        server.setCreatorId(actorId);
        server.setLastUpdaterId(actorId);
        try {
            servers.insert(server);
        } catch (DuplicateKeyException duplicated) {
            throw new BusinessException(ApiCode.CONFLICT, "服务编码 " + request.getServerCode() + " 已存在");
        }

        insertDraft(server.getMcpServerId(), request.getDraft(), actorId);
        log.info("MCP服务已创建，mcpServerId={}，code={}", server.getMcpServerId(), server.getServerCode());
        return detail(server.getMcpServerId());
    }

    /**
     * 修改服务资料
     * <p>
     * 只修改名称与说明，按目录修订号条件更新。
     */
    public McpServerVO updateProfile(long mcpServerId, McpServerProfileDTO request) {
        McpServerPO server = new McpServerPO();
        server.setMcpServerId(mcpServerId);
        server.setServerName(request.getServerName().trim());
        server.setDescription(request.getDescription().trim());
        server.setRevision(request.getRevision());
        server.setLastUpdaterId(ConfigRules.actor());

        requireServer(mcpServerId);
        ConfigRules.requireChanged(servers.updateProfile(server), CONFLICT);
        return summary(mcpServerId);
    }

    /**
     * 启用或停用服务
     * <p>
     * 停用条件更新在同一语句复核没有生效引用；失败时区分“存在引用”和“修订号变化”给出提示。
     */
    public McpServerVO changeStatus(long mcpServerId, ConfigStatus status, long revision) {
        requireServer(mcpServerId);

        McpServerPO server = new McpServerPO();
        server.setMcpServerId(mcpServerId);
        server.setStatus(status);
        server.setRevision(revision);
        server.setLastUpdaterId(ConfigRules.actor());
        if (servers.updateStatus(server) != 1) {
            if (status == ConfigStatus.DISABLED) {
                requireNoActiveUsage(mcpServerId);
            }
            throw new BusinessException(ApiCode.CONFLICT, CONFLICT);
        }

        log.info("MCP服务状态已更新，mcpServerId={}，status={}", mcpServerId, status);
        return summary(mcpServerId);
    }

    /**
     * 读取版本
     */
    public McpServerVersionVO readVersion(long mcpServerId, long versionId) {
        return converter.toView(requireVersion(mcpServerId, versionId));
    }

    /**
     * 新建版本草稿
     * <p>
     * 每个服务最多一个草稿，并发新建由唯一索引拒绝。
     */
    @Transactional
    public McpServerVersionVO createDraft(long mcpServerId, McpServerDraftDTO request) {
        requireServer(mcpServerId);
        validateDraft(request);

        long versionId = insertDraft(mcpServerId, request, ConfigRules.actor());
        return readVersion(mcpServerId, versionId);
    }

    /**
     * 保存版本草稿
     * <p>
     * 只更新草稿状态且修订号匹配的版本，已发布版本保持不变。
     */
    public McpServerVersionVO saveDraft(long mcpServerId, long versionId, McpServerDraftDTO request) {
        if (request.getRevision() == null) {
            throw new BusinessException(ApiCode.BAD_REQUEST, "编辑草稿需要修订号");
        }
        requireServer(mcpServerId);
        validateDraft(request);

        McpServerVersionPO draft = toVersion(mcpServerId, request);
        draft.setMcpServerVersionId(versionId);
        draft.setRevision(request.getRevision());
        draft.setLastUpdaterId(ConfigRules.actor());
        ConfigRules.requireChanged(versions.updateDraft(draft), CONFLICT);
        return readVersion(mcpServerId, versionId);
    }

    /**
     * 发布版本
     * <p>
     * 重新校验凭据后把草稿冻结为已发布版本；停用的服务不能发布。
     */
    @Transactional
    public McpServerVersionVO publish(long mcpServerId, long versionId, long revision) {
        McpServerPO server = requireServer(mcpServerId);
        if (server.getStatus() != ConfigStatus.ENABLED) {
            throw new BusinessException(ApiCode.CONFLICT, "停用的服务不能发布，请先启用");
        }
        McpServerVersionPO version = requireVersion(mcpServerId, versionId);
        credentials.requireConfigured(version.getCredentialRef());

        version.setRevision(revision);
        version.setLastUpdaterId(ConfigRules.actor());
        ConfigRules.requireChanged(versions.publish(version), CONFLICT);
        log.info("MCP服务版本已发布，mcpServerId={}，versionNo={}", mcpServerId, version.getVersionNo());
        return readVersion(mcpServerId, versionId);
    }

    /**
     * 测试连接
     * <p>
     * 按未保存的连接配置建立临时连接并列出远程工具，连接随后关闭；连接失败返回服务不可用及原因。
     */
    public McpProbeVO probe(McpServerDraftDTO request) {
        validateDraft(request);
        McpConnectionBO connection = connectionOf("probe", toVersion(0L, request));

        long startedAt = System.nanoTime();
        List<McpRemoteToolBO> tools = discover(connection);

        McpProbeVO probe = new McpProbeVO();
        probe.setDurationMs(Duration.ofNanos(System.nanoTime() - startedAt).toMillis());
        probe.setTools(tools.stream().map(converter::toView).toList());
        return probe;
    }

    /**
     * 列出可绑定的服务版本
     * <p>
     * 供工具配置选择，只包含启用服务的已发布版本。
     */
    public List<McpServerOptionVO> options() {
        return versions.selectUsableOptions().stream().map(converter::toView).toList();
    }

    /**
     * 发现已发布版本的远程工具
     * <p>
     * 工具配置选择远程工具时调用，服务版本须可用。
     */
    public List<McpRemoteToolVO> remoteTools(long mcpServerVersionId) {
        McpConnectionBO connection = usableConnection(mcpServerVersionId);
        return discover(connection).stream().map(converter::toView).toList();
    }

    /**
     * 解析可用连接
     * <p>
     * 校验服务版本已发布且服务启用，并以已配置的凭据替换请求头占位符；供工具保存、试运行与运行时装配使用。
     */
    public McpConnectionBO usableConnection(long mcpServerVersionId) {
        McpServerVersionPO version = versions.selectOne(Wrappers.<McpServerVersionPO>lambdaQuery()
                .eq(McpServerVersionPO::getMcpServerVersionId, mcpServerVersionId));
        McpServerPO server = version == null ? null : findServer(version.getMcpServerId());
        boolean usable = server != null
                && version.getStatus() == VersionStatus.PUBLISHED
                && server.getStatus() == ConfigStatus.ENABLED;
        if (!usable) {
            throw new BusinessException(ApiCode.BAD_REQUEST, "MCP 服务版本未发布或服务已停用");
        }

        String clientName = server.getServerCode() + "-v" + version.getVersionNo();
        return connectionOf(clientName, version);
    }

    /**
     * 登记引用前锁定服务
     * <p>
     * 以条件更新确认服务仍启用并持有行锁到事务结束，与停用操作串行，避免停用检查遗漏新引用。
     */
    void lockEnabledForReference(long mcpServerVersionId) {
        McpServerVersionPO version = versions.selectOne(Wrappers.<McpServerVersionPO>lambdaQuery()
                .eq(McpServerVersionPO::getMcpServerVersionId, mcpServerVersionId));
        boolean usable = version != null
                && version.getStatus() == VersionStatus.PUBLISHED
                && servers.confirmEnabled(version.getMcpServerId()) == 1;
        if (!usable) {
            throw new BusinessException(ApiCode.CONFLICT, "MCP 服务版本未发布或服务已停用");
        }
    }

    /**
     * 服务展示标签
     */
    String label(long mcpServerVersionId) {
        McpServerVersionPO version = versions.selectOne(Wrappers.<McpServerVersionPO>lambdaQuery()
                .eq(McpServerVersionPO::getMcpServerVersionId, mcpServerVersionId));
        if (version == null) {
            return "未知服务";
        }
        McpServerPO server = findServer(version.getMcpServerId());
        String name = server == null ? "未知服务" : server.getServerName();
        return name + " · v" + version.getVersionNo();
    }

    private List<McpRemoteToolBO> discover(McpConnectionBO connection) {
        try {
            return mcpClients.discover(connection);
        } catch (RuntimeException failed) {
            String reason = failed.getMessage() == null ? failed.getClass().getSimpleName() : failed.getMessage();
            log.warn("MCP连接失败，client={}，原因={}", connection.getClientName(), failed.getClass().getSimpleName());
            throw new BusinessException(ApiCode.SERVICE_UNAVAILABLE,
                    "连接 MCP 服务失败：" + HttpToolTemplate.mask(reason, connection.getCredential()));
        }
    }

    /**
     * 生成连接参数
     * <p>
     * 解析凭据并替换请求头中的凭据占位符；返回值含密钥，只在服务端内存中使用。
     */
    private McpConnectionBO connectionOf(String clientName, McpServerVersionPO version) {
        String credential = credentials.resolve(version.getCredentialRef());
        Map<String, String> resolvedHeaders = new LinkedHashMap<>();
        for (KeyValueBO header : version.getRequestHeaders()) {
            String value = header.getValue().replace("{{" + HttpToolTemplate.CREDENTIAL + "}}",
                    credential == null ? "" : credential);
            resolvedHeaders.put(header.getName(), value);
        }

        McpConnectionBO connection = new McpConnectionBO();
        connection.setClientName(clientName);
        connection.setTransportType(version.getTransportType());
        connection.setEndpoint(version.getEndpoint());
        connection.setHeaders(resolvedHeaders);
        connection.setTimeoutMs(version.getTimeoutMs());
        connection.setCredential(credential);
        return connection;
    }

    private long insertDraft(long mcpServerId, McpServerDraftDTO request, long actorId) {
        McpServerVersionPO draft = toVersion(mcpServerId, request);
        draft.setMcpServerVersionId(IdWorker.getId());
        draft.setVersionNo(versions.nextVersionNo(mcpServerId));
        draft.setStatus(VersionStatus.DRAFT);
        draft.setRevision(0L);
        draft.setCreatorId(actorId);
        draft.setLastUpdaterId(actorId);
        try {
            versions.insert(draft);
        } catch (DuplicateKeyException duplicated) {
            throw new BusinessException(ApiCode.CONFLICT, "该服务已有未发布的草稿，请编辑现有草稿");
        }
        return draft.getMcpServerVersionId();
    }

    private McpServerVersionPO toVersion(long mcpServerId, McpServerDraftDTO request) {
        McpServerVersionPO version = new McpServerVersionPO();
        version.setMcpServerId(mcpServerId);
        version.setTransportType(request.getTransportType());
        version.setEndpoint(request.getEndpoint().trim());
        version.setCredentialRef(blankToNull(request.getCredentialRef()));
        version.setRequestHeaders(converter.toHeaders(request.getHeaders()));
        version.setTimeoutMs(request.getTimeoutMs());
        return version;
    }

    /**
     * 校验连接草稿
     * <p>
     * 地址只允许 http/https 且不含用户信息和片段；请求头不能使用系统维护的名称；引用凭据时必须选择已配置的凭据。
     */
    private void validateDraft(McpServerDraftDTO request) {
        URI uri;
        try {
            uri = URI.create(request.getEndpoint().trim());
        } catch (IllegalArgumentException invalid) {
            throw new BusinessException(ApiCode.BAD_REQUEST, "连接地址格式无效");
        }
        boolean httpScheme = "http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme());
        if (!httpScheme || uri.getHost() == null || uri.getRawUserInfo() != null || uri.getRawFragment() != null) {
            throw new BusinessException(ApiCode.BAD_REQUEST, "连接地址须为 http/https，且不能包含用户信息或 # 片段");
        }

        boolean usesCredential = false;
        for (KeyValueDTO header : request.getHeaders()) {
            if (!header.getName().matches("[A-Za-z0-9-]{1,100}")) {
                throw new BusinessException(ApiCode.BAD_REQUEST, "请求头名称只能包含字母、数字和连字符");
            }
            if (FORBIDDEN_HEADERS.contains(header.getName().toLowerCase(Locale.ROOT))) {
                throw new BusinessException(ApiCode.BAD_REQUEST, "请求头 " + header.getName() + " 由系统维护，不能配置");
            }
            usesCredential = usesCredential || header.getValue().contains("{{" + HttpToolTemplate.CREDENTIAL + "}}");
        }

        String credentialRef = blankToNull(request.getCredentialRef());
        if (usesCredential && credentialRef == null) {
            throw new BusinessException(ApiCode.BAD_REQUEST, "请求头使用了 {{credential}}，请选择凭据");
        }
        credentials.requireConfigured(credentialRef);
    }

    private void requireNoActiveUsage(long mcpServerId) {
        List<String> owners = servers.selectUsages(mcpServerId).stream()
                .filter(ConfigUsageBO::isActive)
                .map(usage -> usage.getOwnerName() + "（" + usage.getOwnerCode() + "）")
                .distinct()
                .toList();
        if (!owners.isEmpty()) {
            throw new BusinessException(ApiCode.CONFLICT, "以下启用的工具正在使用该服务，请先停用或改用其他服务：" + String.join("、", owners));
        }
    }

    private McpServerVO summary(long mcpServerId) {
        List<McpServerSummaryBO> summaries = servers.selectSummaries(mcpServerId);
        if (summaries.isEmpty()) {
            throw new BusinessException(ApiCode.NOT_FOUND, "MCP 服务不存在");
        }
        return converter.toView(summaries.get(0));
    }

    private McpServerPO requireServer(long mcpServerId) {
        McpServerPO server = findServer(mcpServerId);
        if (server == null) {
            throw new BusinessException(ApiCode.NOT_FOUND, "MCP 服务不存在");
        }
        return server;
    }

    private McpServerPO findServer(long mcpServerId) {
        return servers.selectOne(Wrappers.<McpServerPO>lambdaQuery().eq(McpServerPO::getMcpServerId, mcpServerId));
    }

    private McpServerVersionPO requireVersion(long mcpServerId, long versionId) {
        McpServerVersionPO version = versions.selectOne(Wrappers.<McpServerVersionPO>lambdaQuery()
                .eq(McpServerVersionPO::getMcpServerId, mcpServerId)
                .eq(McpServerVersionPO::getMcpServerVersionId, versionId));
        if (version == null) {
            throw new BusinessException(ApiCode.NOT_FOUND, "MCP 服务版本不存在");
        }
        return version;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
