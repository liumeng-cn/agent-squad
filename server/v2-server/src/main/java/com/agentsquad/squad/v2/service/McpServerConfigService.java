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
import com.agentsquad.squad.v2.dto.McpServerProbeDTO;
import com.agentsquad.squad.v2.dto.McpServerSaveDTO;
import com.agentsquad.squad.v2.enums.ConfigReadiness;
import com.agentsquad.squad.v2.enums.ConfigStatus;
import com.agentsquad.squad.v2.mapper.McpServerMapper;
import com.agentsquad.squad.v2.mapper.McpServerVersionMapper;
import com.agentsquad.squad.v2.po.McpServerPO;
import com.agentsquad.squad.v2.po.McpServerVersionPO;
import com.agentsquad.squad.v2.utils.ConfigCodeGenerator;
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
 * 维护远程 MCP 服务的目录、配置版本历史、启停、逻辑删除与实时探测。
 * MCP 不允许单独发布，每次保存改动均生成新的不可变配置版本；仅启用且就绪的 MCP 可被工具关联。
 */
@Slf4j
@Service
public class McpServerConfigService {
    private static final String CONFLICT = "MCP 服务已被其他操作修改，请刷新后重试";

    private static final int MAX_CODE_RETRIES = 5;

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
     * 返回服务摘要、最新配置版本、历史配置版本清单与下游引用关系。
     */
    public McpServerDetailVO detail(long mcpServerId) {
        McpServerVO server = summary(mcpServerId);
        McpServerDetailVO detail = new McpServerDetailVO();
        detail.setServer(server);
        if (server.getLatestVersionId() != null) {
            long latestVersionId = ConfigRules.parseId(server.getLatestVersionId(), "MCP 版本ID");
            detail.setLatestVersion(readVersion(mcpServerId, latestVersionId));
        }
        detail.setVersions(versions.selectSummaries(mcpServerId).stream().map(converter::toView).toList());
        detail.setUsages(servers.selectUsages(mcpServerId).stream().map(converter::toView).toList());
        return detail;
    }

    /**
     * 新增 MCP 服务
     * <p>
     * 系统自动生成带日期的随机编码，同一事务写入目录与第 1 个配置版本。
     */
    @Transactional
    public McpServerDetailVO create(McpServerSaveDTO request) {
        long actorId = ConfigRules.actor();
        McpServerVersionPO version = buildVersion(request);

        McpServerPO server = new McpServerPO();
        server.setMcpServerId(IdWorker.getId());
        server.setServerName(request.getServerName().trim());
        server.setDescription(trimToEmpty(request.getDescription()));
        server.setStatus(ConfigStatus.ENABLED);
        server.setLatestVersionNo(0);
        server.setLatestReadiness(version.getReadiness());
        server.setRevision(0L);
        server.setCreatorId(actorId);
        server.setLastUpdaterId(actorId);
        insertWithGeneratedCode(server);

        version.setMcpServerId(server.getMcpServerId());
        version.setMcpServerVersionId(IdWorker.getId());
        version.setVersionNo(1);
        version.setCreatorId(actorId);
        version.setLastUpdaterId(actorId);
        versions.insert(version);

        McpServerPO pointer = new McpServerPO();
        pointer.setMcpServerId(server.getMcpServerId());
        pointer.setServerName(server.getServerName());
        pointer.setDescription(server.getDescription());
        pointer.setLatestVersionId(version.getMcpServerVersionId());
        pointer.setLatestVersionNo(version.getVersionNo());
        pointer.setLatestReadiness(version.getReadiness());
        pointer.setRevision(0L);
        pointer.setLastUpdaterId(actorId);
        ConfigRules.requireChanged(servers.updateLatest(pointer), CONFLICT);

        log.info("MCP服务已创建，mcpServerId={}，code={}，readiness={}",
                server.getMcpServerId(), server.getServerCode(), version.getReadiness());
        return detail(server.getMcpServerId());
    }

    /**
     * 保存 MCP 服务改动
     * <p>
     * 每次保存新增一条配置版本并按目录修订号条件更新最新指针，不影响已发布的 Master 快照。
     */
    @Transactional
    public McpServerDetailVO save(long mcpServerId, McpServerSaveDTO request) {
        if (request.getRevision() == null) {
            throw new BusinessException(ApiCode.BAD_REQUEST, "保存配置需要携带修订号");
        }
        requireServer(mcpServerId);
        long actorId = ConfigRules.actor();
        McpServerVersionPO version = buildVersion(request);

        int nextVersionNo = versions.nextVersionNo(mcpServerId);
        version.setMcpServerId(mcpServerId);
        version.setMcpServerVersionId(IdWorker.getId());
        version.setVersionNo(nextVersionNo);
        version.setCreatorId(actorId);
        version.setLastUpdaterId(actorId);
        versions.insert(version);

        McpServerPO update = new McpServerPO();
        update.setMcpServerId(mcpServerId);
        update.setServerName(request.getServerName().trim());
        update.setDescription(trimToEmpty(request.getDescription()));
        update.setLatestVersionId(version.getMcpServerVersionId());
        update.setLatestVersionNo(nextVersionNo);
        update.setLatestReadiness(version.getReadiness());
        update.setRevision(request.getRevision());
        update.setLastUpdaterId(actorId);
        ConfigRules.requireChanged(servers.updateLatest(update), CONFLICT);

        log.info("MCP服务已保存新配置版本，mcpServerId={}，versionNo={}，readiness={}",
                mcpServerId, nextVersionNo, version.getReadiness());
        return detail(mcpServerId);
    }

    /**
     * 启用或停用服务
     * <p>
     * 停用前确认没有启用中的下游工具或 Master 生效快照引用该服务。
     */
    @Transactional
    public McpServerVO changeStatus(long mcpServerId, ConfigStatus status, long revision) {
        requireServer(mcpServerId);

        McpServerPO server = new McpServerPO();
        server.setMcpServerId(mcpServerId);
        server.setStatus(status);
        server.setRevision(revision);
        server.setLastUpdaterId(ConfigRules.actor());
        ConfigRules.requireChanged(servers.updateStatus(server), CONFLICT);
        if (status == ConfigStatus.DISABLED) {
            requireNoActiveUsage(mcpServerId, "停用");
        }

        log.info("MCP服务状态已更新，mcpServerId={}，status={}", mcpServerId, status);
        return summary(mcpServerId);
    }

    /**
     * 逻辑删除服务
     * <p>
     * 保留全部配置历史与已发布快照；若仍被未删除下游工具或生效发布引用，拒绝删除并提示引用方。
     */
    @Transactional
    public void delete(long mcpServerId, long revision) {
        requireServer(mcpServerId);
        requireNoAnyUsage(mcpServerId);

        McpServerPO server = new McpServerPO();
        server.setMcpServerId(mcpServerId);
        server.setRevision(revision);
        server.setLastUpdaterId(ConfigRules.actor());
        ConfigRules.requireChanged(servers.softDelete(server), CONFLICT);
        log.info("MCP服务已逻辑删除，mcpServerId={}", mcpServerId);
    }

    /**
     * 只读读取指定历史配置版本
     */
    public McpServerVersionVO readVersion(long mcpServerId, long versionId) {
        return converter.toView(requireVersion(mcpServerId, versionId));
    }

    /**
     * 按版本业务ID只读读取历史版本（供发布快照穿透查看使用）
     */
    public McpServerVersionVO readVersionById(long mcpServerVersionId) {
        McpServerVersionPO version = versions.selectOne(Wrappers.<McpServerVersionPO>lambdaQuery()
                .eq(McpServerVersionPO::getMcpServerVersionId, mcpServerVersionId));
        if (version == null) {
            throw new BusinessException(ApiCode.NOT_FOUND, "MCP 配置版本不存在");
        }
        return converter.toView(version);
    }

    /**
     * 实时探测连接
     * <p>
     * 在事务外使用当前未保存表单参数建立临时连接并列出远程工具。
     */
    public McpProbeVO probe(McpServerProbeDTO request) {
        validateEndpointAndHeaders(request.getEndpoint(), request.getHeaders(), request.getCredentialRef(), true);

        McpServerVersionPO temp = new McpServerVersionPO();
        temp.setTransportType(request.getTransportType());
        temp.setEndpoint(request.getEndpoint().trim());
        temp.setCredentialRef(blankToNull(request.getCredentialRef()));
        temp.setRequestHeaders(converter.toHeaders(request.getHeaders()));
        temp.setTimeoutMs(request.getTimeoutMs());

        McpConnectionBO connection = connectionOf("probe", temp);
        long startedAt = System.nanoTime();
        List<McpRemoteToolBO> tools = discover(connection);

        McpProbeVO probe = new McpProbeVO();
        probe.setDurationMs(Duration.ofNanos(System.nanoTime() - startedAt).toMillis());
        probe.setTools(tools.stream().map(converter::toView).toList());
        return probe;
    }

    /**
     * 列出可关联的 MCP 服务（启用且最新配置就绪）
     */
    public List<McpServerOptionVO> options() {
        return versions.selectUsableOptions().stream().map(converter::toView).toList();
    }

    /**
     * 发现指定 MCP 服务当前最新配置版本的远程工具
     */
    public List<McpRemoteToolVO> remoteTools(long mcpServerId) {
        McpConnectionBO connection = usableConnection(mcpServerId);
        return discover(connection).stream().map(converter::toView).toList();
    }

    /**
     * 解析 MCP 服务最新配置版本的可用连接
     * <p>
     * 校验服务未删除、已启用且最新配置就绪；供工具关联校验、远程发现和试运行使用。
     */
    public McpConnectionBO usableConnection(long mcpServerId) {
        McpServerPO server = requireUsableServer(mcpServerId);
        McpServerVersionPO version = requireVersion(mcpServerId, server.getLatestVersionId());
        String clientName = server.getServerCode() + "-v" + version.getVersionNo();
        return connectionOf(clientName, version);
    }

    /**
     * 解析发布快照定格的指定 MCP 配置版本连接参数
     * <p>
     * 按定格版本号精确读取，不受后续新增配置版本或逻辑删除影响。
     */
    public McpConnectionBO releaseConnection(long mcpServerId, long mcpServerVersionId) {
        McpServerPO server = findServerIncludingDeleted(mcpServerId);
        McpServerVersionPO version = requireVersion(mcpServerId, mcpServerVersionId);
        String code = server == null ? ("MCP-" + mcpServerId) : server.getServerCode();
        return connectionOf(code + "-v" + version.getVersionNo(), version);
    }

    /**
     * 校验 MCP 服务处于未删除、启用且最新配置就绪状态
     */
    public McpServerPO requireUsableServer(long mcpServerId) {
        McpServerPO server = findActiveServer(mcpServerId);
        if (server == null) {
            throw new BusinessException(ApiCode.BAD_REQUEST, "所选 MCP 服务不存在或已删除");
        }
        if (server.getStatus() != ConfigStatus.ENABLED) {
            throw new BusinessException(ApiCode.BAD_REQUEST, "MCP 服务 " + server.getServerName() + " 已停用，不可关联");
        }
        if (server.getLatestReadiness() != ConfigReadiness.READY || server.getLatestVersionId() == null) {
            throw new BusinessException(ApiCode.BAD_REQUEST, "MCP 服务 " + server.getServerName() + " 配置未就绪，不可关联");
        }
        return server;
    }

    /**
     * 发布事务内复核 MCP 服务仍处于启用且就绪状态
     */
    public void lockEnabledReady(long mcpServerId) {
        if (servers.confirmEnabledReady(mcpServerId) != 1) {
            McpServerPO server = findServerIncludingDeleted(mcpServerId);
            String name = server == null ? String.valueOf(mcpServerId) : server.getServerName();
            throw new BusinessException(ApiCode.CONFLICT, "依赖的 MCP 服务 " + name + " 已停用、删除或未就绪，请刷新后重试");
        }
    }

    /**
     * 查询服务展示名称（含已逻辑删除的历史服务）
     */
    public String serverName(long mcpServerId) {
        McpServerPO server = findServerIncludingDeleted(mcpServerId);
        return server == null ? "未知服务" : server.getServerName();
    }

    /**
     * 服务标签（用于试运行摘要）
     */
    public String label(long mcpServerId) {
        McpServerPO server = findServerIncludingDeleted(mcpServerId);
        if (server == null) {
            return "未知服务";
        }
        return server.getServerName() + " · v" + server.getLatestVersionNo();
    }

    private void insertWithGeneratedCode(McpServerPO server) {
        String prefix = ConfigCodeGenerator.mcpServerPrefix();
        for (int attempt = 0; attempt < MAX_CODE_RETRIES; attempt++) {
            String latestCode = servers.selectMaxCodeByPrefix(prefix);
            server.setServerCode(ConfigCodeGenerator.nextCode(prefix, latestCode, attempt));
            try {
                servers.insert(server);
                return;
            } catch (DuplicateKeyException duplicated) {
                log.warn("自动生成MCP编码发生并发碰撞，准备递增重试，code={}", server.getServerCode());
            }
        }
        throw new BusinessException(ApiCode.CONFLICT, "生成 MCP 服务编码失败，请重试");
    }

    private McpServerVersionPO buildVersion(McpServerSaveDTO request) {
        String endpoint = trimToEmpty(request.getEndpoint());
        String credentialRef = blankToNull(request.getCredentialRef());
        List<KeyValueDTO> headers = request.getHeaders() == null ? List.of() : request.getHeaders();

        validateEndpointAndHeaders(endpoint, headers, credentialRef, false);

        boolean ready = request.getTransportType() != null
                && !endpoint.isBlank()
                && (!usesCredential(headers) || credentialRef != null);

        McpServerVersionPO version = new McpServerVersionPO();
        version.setTransportType(request.getTransportType());
        version.setEndpoint(endpoint);
        version.setCredentialRef(credentialRef);
        version.setRequestHeaders(converter.toHeaders(headers));
        version.setTimeoutMs(request.getTimeoutMs() == null ? 30000 : request.getTimeoutMs());
        version.setReadiness(ready ? ConfigReadiness.READY : ConfigReadiness.INCOMPLETE);
        return version;
    }

    private void validateEndpointAndHeaders(String rawEndpoint, List<KeyValueDTO> headers,
                                            String rawCredentialRef, boolean strict) {
        String endpoint = trimToEmpty(rawEndpoint);
        if (strict && endpoint.isBlank()) {
            throw new BusinessException(ApiCode.BAD_REQUEST, "请填写连接地址");
        }
        if (!endpoint.isBlank()) {
            URI uri;
            try {
                uri = URI.create(endpoint);
            } catch (IllegalArgumentException invalid) {
                throw new BusinessException(ApiCode.BAD_REQUEST, "连接地址格式无效");
            }
            boolean httpScheme = "http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme());
            if (!httpScheme || uri.getHost() == null || uri.getRawUserInfo() != null || uri.getRawFragment() != null) {
                throw new BusinessException(ApiCode.BAD_REQUEST, "连接地址须为 http/https，且不能包含用户信息或 # 片段");
            }
        }

        for (KeyValueDTO header : headers) {
            if (!header.getName().matches("[A-Za-z0-9-]{1,100}")) {
                throw new BusinessException(ApiCode.BAD_REQUEST, "请求头名称只能包含字母、数字和连字符");
            }
            if (FORBIDDEN_HEADERS.contains(header.getName().toLowerCase(Locale.ROOT))) {
                throw new BusinessException(ApiCode.BAD_REQUEST, "请求头 " + header.getName() + " 由系统维护，不能配置");
            }
        }

        String credentialRef = blankToNull(rawCredentialRef);
        if (strict && usesCredential(headers) && credentialRef == null) {
            throw new BusinessException(ApiCode.BAD_REQUEST, "请求头使用了 {{credential}}，请选择凭据");
        }
        if (credentialRef != null) {
            credentials.requireConfigured(credentialRef);
        }
    }

    private boolean usesCredential(List<KeyValueDTO> headers) {
        String placeholder = "{{" + HttpToolTemplate.CREDENTIAL + "}}";
        for (KeyValueDTO header : headers) {
            if (header.getValue() != null && header.getValue().contains(placeholder)) {
                return true;
            }
        }
        return false;
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

    private McpConnectionBO connectionOf(String clientName, McpServerVersionPO version) {
        String credential = credentials.resolve(version.getCredentialRef());
        Map<String, String> resolvedHeaders = new LinkedHashMap<>();
        if (version.getRequestHeaders() != null) {
            for (KeyValueBO header : version.getRequestHeaders()) {
                String value = header.getValue().replace("{{" + HttpToolTemplate.CREDENTIAL + "}}",
                        credential == null ? "" : credential);
                resolvedHeaders.put(header.getName(), value);
            }
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

    private void requireNoActiveUsage(long mcpServerId, String action) {
        List<String> owners = servers.selectUsages(mcpServerId).stream()
                .filter(ConfigUsageBO::isActive)
                .map(usage -> usage.getOwnerName() + "（" + usage.getOwnerCode() + "）")
                .distinct()
                .toList();
        if (!owners.isEmpty()) {
            throw new BusinessException(ApiCode.CONFLICT,
                    "以下启用的资源正在使用该 MCP 服务，无法" + action + "：" + String.join("、", owners));
        }
    }

    private void requireNoAnyUsage(long mcpServerId) {
        List<String> owners = servers.selectUsages(mcpServerId).stream()
                .map(usage -> usage.getOwnerName() + "（" + usage.getOwnerCode() + "）")
                .distinct()
                .toList();
        if (!owners.isEmpty()) {
            throw new BusinessException(ApiCode.CONFLICT,
                    "以下资源仍引用该 MCP 服务，请先调整关联并重新发布后再删除：" + String.join("、", owners));
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
        McpServerPO server = findActiveServer(mcpServerId);
        if (server == null) {
            throw new BusinessException(ApiCode.NOT_FOUND, "MCP 服务不存在");
        }
        return server;
    }

    private McpServerPO findActiveServer(long mcpServerId) {
        return servers.selectOne(Wrappers.<McpServerPO>lambdaQuery()
                .eq(McpServerPO::getMcpServerId, mcpServerId)
                .isNull(McpServerPO::getDeletedAt));
    }

    private McpServerPO findServerIncludingDeleted(long mcpServerId) {
        return servers.selectOne(Wrappers.<McpServerPO>lambdaQuery()
                .eq(McpServerPO::getMcpServerId, mcpServerId));
    }

    private McpServerVersionPO requireVersion(long mcpServerId, long versionId) {
        McpServerVersionPO version = versions.selectOne(Wrappers.<McpServerVersionPO>lambdaQuery()
                .eq(McpServerVersionPO::getMcpServerId, mcpServerId)
                .eq(McpServerVersionPO::getMcpServerVersionId, versionId));
        if (version == null) {
            throw new BusinessException(ApiCode.NOT_FOUND, "MCP 服务配置版本不存在");
        }
        return version;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String trimToEmpty(String value) {
        return value == null ? "" : value.trim();
    }
}
