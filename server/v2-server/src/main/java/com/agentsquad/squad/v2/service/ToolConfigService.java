package com.agentsquad.squad.v2.service;

import com.agentsquad.squad.common.enums.ApiCode;
import com.agentsquad.squad.common.exceptions.BusinessException;
import com.agentsquad.squad.v2.agent.tool.AskUserTool;
import com.agentsquad.squad.v2.agent.tool.ConfiguredToolRunner;
import com.agentsquad.squad.v2.agent.tool.builtin.BuiltinToolRegistry;
import com.agentsquad.squad.v2.agent.tool.http.HttpToolTemplate;
import com.agentsquad.squad.v2.bo.BuiltinToolBO;
import com.agentsquad.squad.v2.bo.ConfigUsageBO;
import com.agentsquad.squad.v2.bo.HttpToolSpecBO;
import com.agentsquad.squad.v2.bo.ToolDefinitionBO;
import com.agentsquad.squad.v2.bo.ToolRunResultBO;
import com.agentsquad.squad.v2.bo.ToolSummaryBO;
import com.agentsquad.squad.v2.converter.ToolConfigConverter;
import com.agentsquad.squad.v2.dto.ToolSaveDTO;
import com.agentsquad.squad.v2.dto.ToolTestRunDTO;
import com.agentsquad.squad.v2.enums.ConfigReadiness;
import com.agentsquad.squad.v2.enums.ConfigStatus;
import com.agentsquad.squad.v2.enums.ToolType;
import com.agentsquad.squad.v2.mapper.ToolMapper;
import com.agentsquad.squad.v2.mapper.ToolVersionMapper;
import com.agentsquad.squad.v2.po.ToolPO;
import com.agentsquad.squad.v2.po.ToolVersionPO;
import com.agentsquad.squad.v2.utils.ConfigCodeGenerator;
import com.agentsquad.squad.v2.utils.ConfigRules;
import com.agentsquad.squad.v2.utils.ToolSchemaRules;
import com.agentsquad.squad.v2.vo.BuiltinToolOptionVO;
import com.agentsquad.squad.v2.vo.ToolDetailVO;
import com.agentsquad.squad.v2.vo.ToolOptionVO;
import com.agentsquad.squad.v2.vo.ToolTestRunVO;
import com.agentsquad.squad.v2.vo.ToolVO;
import com.agentsquad.squad.v2.vo.ToolVersionVO;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 工具配置管理
 * <p>
 * 维护内置、HTTP 与 MCP 三类工具的目录、配置版本历史、启停、逻辑删除与试运行。
 * 工具不允许单独发布，每次保存改动均生成新的不可变配置版本；仅启用且就绪的工具可被 Agent 关联。
 */
@Slf4j
@Service
public class ToolConfigService {
    private static final String CONFLICT = "工具已被其他操作修改，请刷新后重试";

    private static final int MAX_CODE_RETRIES = 5;

    private static final Map<String, Object> EMPTY_SCHEMA = Map.of("type", "object", "properties", Map.of());

    @Resource
    private ToolMapper tools;

    @Resource
    private ToolVersionMapper versions;

    @Resource
    private ToolConfigConverter converter;

    @Resource
    private McpServerConfigService mcpServers;

    @Resource
    private CredentialService credentials;

    @Resource
    private BuiltinToolRegistry builtinTools;

    @Resource
    private ConfiguredToolRunner toolRunner;

    /**
     * 查询工具列表
     */
    public List<ToolVO> list() {
        return tools.selectSummaries(null).stream().map(converter::toView).toList();
    }

    /**
     * 查询工具详情
     * <p>
     * 返回工具摘要、最新配置版本、历史配置版本清单与下游 Agent 引用关系。
     */
    public ToolDetailVO detail(long toolId) {
        ToolVO tool = summary(toolId);
        ToolDetailVO detail = new ToolDetailVO();
        detail.setTool(tool);
        if (tool.getLatestVersionId() != null) {
            long latestVersionId = ConfigRules.parseId(tool.getLatestVersionId(), "工具版本ID");
            detail.setLatestVersion(readVersion(toolId, latestVersionId));
        }
        detail.setVersions(versions.selectSummaries(toolId).stream().map(converter::toView).toList());
        detail.setUsages(tools.selectUsages(toolId).stream().map(converter::toView).toList());
        return detail;
    }

    /**
     * 新增工具
     * <p>
     * 系统自动生成带日期的随机编码，同一事务写入工具目录与第 1 个配置版本。
     */
    @Transactional
    public ToolDetailVO create(ToolSaveDTO request) {
        if (request.getToolType() == null) {
            throw new BusinessException(ApiCode.BAD_REQUEST, "新增工具必须指定工具类型");
        }
        long actorId = ConfigRules.actor();
        ToolVersionPO version = buildVersion(request.getToolType(), request, false);

        ToolPO tool = new ToolPO();
        tool.setToolId(IdWorker.getId());
        tool.setToolName(request.getToolName().trim());
        tool.setDisplayAlias(trimToEmpty(request.getDisplayAlias()));
        tool.setToolType(request.getToolType());
        tool.setStatus(ConfigStatus.ENABLED);
        tool.setLatestVersionNo(0);
        tool.setLatestReadiness(version.getReadiness());
        tool.setRevision(0L);
        tool.setCreatorId(actorId);
        tool.setLastUpdaterId(actorId);
        insertWithGeneratedCode(tool);

        version.setToolId(tool.getToolId());
        version.setToolVersionId(IdWorker.getId());
        version.setVersionNo(1);
        version.setCreatorId(actorId);
        version.setLastUpdaterId(actorId);
        versions.insert(version);

        ToolPO pointer = new ToolPO();
        pointer.setToolId(tool.getToolId());
        pointer.setToolName(tool.getToolName());
        pointer.setDisplayAlias(tool.getDisplayAlias());
        pointer.setLatestVersionId(version.getToolVersionId());
        pointer.setLatestVersionNo(1);
        pointer.setLatestReadiness(version.getReadiness());
        pointer.setRevision(0L);
        pointer.setLastUpdaterId(actorId);
        ConfigRules.requireChanged(tools.updateLatest(pointer), CONFLICT);

        log.info("工具已创建，toolId={}，code={}，type={}，readiness={}",
                tool.getToolId(), tool.getToolCode(), tool.getToolType(), version.getReadiness());
        return detail(tool.getToolId());
    }

    /**
     * 保存工具改动
     * <p>
     * 每次保存新增一条不可变配置版本并按目录修订号条件更新最新指针，不影响已发布的 Master 快照。
     */
    @Transactional
    public ToolDetailVO save(long toolId, ToolSaveDTO request) {
        if (request.getRevision() == null) {
            throw new BusinessException(ApiCode.BAD_REQUEST, "保存配置需要携带修订号");
        }
        ToolPO existing = requireTool(toolId);
        long actorId = ConfigRules.actor();
        ToolVersionPO version = buildVersion(existing.getToolType(), request, false);

        int nextVersionNo = versions.nextVersionNo(toolId);
        version.setToolId(toolId);
        version.setToolVersionId(IdWorker.getId());
        version.setVersionNo(nextVersionNo);
        version.setCreatorId(actorId);
        version.setLastUpdaterId(actorId);
        versions.insert(version);

        ToolPO update = new ToolPO();
        update.setToolId(toolId);
        update.setToolName(request.getToolName().trim());
        update.setDisplayAlias(trimToEmpty(request.getDisplayAlias()));
        update.setLatestVersionId(version.getToolVersionId());
        update.setLatestVersionNo(nextVersionNo);
        update.setLatestReadiness(version.getReadiness());
        update.setRevision(request.getRevision());
        update.setLastUpdaterId(actorId);
        ConfigRules.requireChanged(tools.updateLatest(update), CONFLICT);

        log.info("工具已保存新配置版本，toolId={}，versionNo={}，readiness={}",
                toolId, nextVersionNo, version.getReadiness());
        return detail(toolId);
    }

    /**
     * 启用或停用工具
     */
    @Transactional
    public ToolVO changeStatus(long toolId, ConfigStatus status, long revision) {
        requireTool(toolId);

        ToolPO tool = new ToolPO();
        tool.setToolId(toolId);
        tool.setStatus(status);
        tool.setRevision(revision);
        tool.setLastUpdaterId(ConfigRules.actor());
        ConfigRules.requireChanged(tools.updateStatus(tool), CONFLICT);
        if (status == ConfigStatus.DISABLED) {
            requireNoActiveUsage(toolId, "停用");
        }

        log.info("工具状态已更新，toolId={}，status={}", toolId, status);
        return summary(toolId);
    }

    /**
     * 逻辑删除工具
     */
    @Transactional
    public void delete(long toolId, long revision) {
        requireTool(toolId);
        requireNoAnyUsage(toolId);

        ToolPO tool = new ToolPO();
        tool.setToolId(toolId);
        tool.setRevision(revision);
        tool.setLastUpdaterId(ConfigRules.actor());
        ConfigRules.requireChanged(tools.softDelete(tool), CONFLICT);
        log.info("工具已逻辑删除，toolId={}", toolId);
    }

    /**
     * 只读读取指定历史配置版本
     */
    public ToolVersionVO readVersion(long toolId, long versionId) {
        ToolVersionPO version = requireVersion(toolId, versionId);
        return toVersionView(version);
    }

    /**
     * 按版本业务ID只读读取历史版本（供发布快照穿透查看使用）
     */
    public ToolVersionVO readVersionById(long toolVersionId) {
        ToolVersionPO version = versions.selectOne(Wrappers.<ToolVersionPO>lambdaQuery()
                .eq(ToolVersionPO::getToolVersionId, toolVersionId));
        if (version == null) {
            throw new BusinessException(ApiCode.NOT_FOUND, "工具配置版本不存在");
        }
        return toVersionView(version);
    }

    /**
     * 试运行未保存的工具定义
     */
    public ToolTestRunVO testRun(ToolTestRunDTO request) {
        ToolVersionPO version = buildVersion(request.getToolType(), request.getDefinition(), true);
        ToolDefinitionBO definition = definitionOf(version);
        ToolRunResultBO result = toolRunner.run(definition, request.getArguments());
        return converter.toView(result);
    }

    /**
     * 列出可选的内置工具实现
     */
    public List<BuiltinToolOptionVO> builtinOptions() {
        return builtinTools.list().stream().map(converter::toView).toList();
    }

    /**
     * 列出可关联的可用工具（启用且最新配置就绪）
     */
    public List<ToolOptionVO> usableOptions() {
        return versions.selectUsableOptions().stream().map(converter::toView).toList();
    }

    /**
     * 按发布快照定格的工具版本与 MCP 版本组装可执行工具定义
     * <p>
     * 运行时仅加载定格版本，不读取目录最新指针。
     */
    public ToolDefinitionBO releaseDefinition(long toolId, long toolVersionId, Long mcpServerId, Long mcpServerVersionId) {
        ToolVersionPO version = requireVersion(toolId, toolVersionId);
        ToolDefinitionBO definition = new ToolDefinitionBO();
        definition.setToolType(version.getToolType());
        definition.setExposedName(version.getExposedName());
        definition.setDescription(version.getDescription());
        definition.setInputSchema(version.getInputSchema());
        definition.setReadOnly(Boolean.TRUE.equals(version.getReadOnly()));
        definition.setImplementationKey(version.getImplementationKey());
        definition.setHttpSpec(version.getHttpSpec());
        definition.setRemoteToolName(version.getRemoteToolName());

        if (version.getHttpSpec() != null) {
            definition.setCredential(credentials.resolve(version.getHttpSpec().getCredentialRef()));
        }
        if (version.getToolType() == ToolType.MCP && mcpServerId != null && mcpServerVersionId != null) {
            definition.setMcpConnection(mcpServers.releaseConnection(mcpServerId, mcpServerVersionId));
            definition.setMcpServerLabel(mcpServers.serverName(mcpServerId));
        }
        return definition;
    }

    /**
     * 发布事务内复核工具仍处于启用且就绪状态
     */
    public void lockEnabledReady(long toolId) {
        if (tools.confirmEnabledReady(toolId) != 1) {
            throw new BusinessException(ApiCode.CONFLICT, "依赖的工具已停用、删除或未就绪，请刷新后重试");
        }
    }

    private ToolVersionVO toVersionView(ToolVersionPO version) {
        ToolVersionVO view = converter.toView(version);
        if (version.getMcpServerId() != null) {
            view.setMcpServerName(mcpServers.serverName(version.getMcpServerId()));
        }
        return view;
    }

    private void insertWithGeneratedCode(ToolPO tool) {
        String prefix = ConfigCodeGenerator.toolPrefix(tool.getToolType());
        for (int attempt = 0; attempt < MAX_CODE_RETRIES; attempt++) {
            String latestCode = tools.selectMaxCodeByPrefix(prefix);
            tool.setToolCode(ConfigCodeGenerator.nextCode(prefix, latestCode, attempt));
            try {
                tools.insert(tool);
                return;
            } catch (DuplicateKeyException duplicated) {
                log.warn("自动生成工具编码发生并发碰撞，准备递增重试，code={}", tool.getToolCode());
            }
        }
        throw new BusinessException(ApiCode.CONFLICT, "生成工具编码失败，请重试");
    }

    /**
     * 组装工具配置版本并计算就绪状态
     * <p>
     * 保存时（strict=false）允许必填字段为空并标记 INCOMPLETE，但所选 MCP 服务必须已启用且就绪；
     * 试运行时（strict=true）要求实现定义完整合法。
     */
    private ToolVersionPO buildVersion(ToolType toolType, ToolSaveDTO request, boolean strict) {
        String exposedName = trimToEmpty(request.getExposedName());
        if (AskUserTool.NAME.equals(exposedName)) {
            throw new BusinessException(ApiCode.BAD_REQUEST, AskUserTool.NAME + " 是平台保留的工具名，请使用其他名称");
        }
        String description = trimToEmpty(request.getDescription());
        Map<String, Object> schema = request.getInputSchema() == null ? EMPTY_SCHEMA : request.getInputSchema();
        Set<String> parameterNames = ToolSchemaRules.validate(schema);

        ToolVersionPO version = new ToolVersionPO();
        version.setToolType(toolType);
        version.setExposedName(exposedName);
        version.setDescription(description);
        version.setInputSchema(schema);
        version.setReadOnly(request.isReadOnly());

        boolean implReady;
        if (toolType == ToolType.BUILTIN) {
            implReady = fillBuiltin(version, request, strict);
        } else if (toolType == ToolType.HTTP) {
            implReady = fillHttp(version, request, parameterNames, strict);
        } else {
            implReady = fillMcp(version, request, strict);
        }

        if (strict) {
            if (exposedName.isBlank()) {
                throw new BusinessException(ApiCode.BAD_REQUEST, "试运行前请填写模型工具名");
            }
            if (!implReady) {
                throw new BusinessException(ApiCode.BAD_REQUEST, "工具实现配置不完整，无法试运行");
            }
        }

        boolean ready = !exposedName.isBlank() && !description.isBlank() && implReady;
        version.setReadiness(ready ? ConfigReadiness.READY : ConfigReadiness.INCOMPLETE);
        return version;
    }

    private boolean fillBuiltin(ToolVersionPO version, ToolSaveDTO request, boolean strict) {
        String key = blankToNull(request.getImplementationKey());
        if (key == null) {
            if (strict) {
                throw new BusinessException(ApiCode.BAD_REQUEST, "请选择有效的内置实现");
            }
            return false;
        }
        BuiltinToolBO builtin = builtinTools.find(key);
        if (builtin == null) {
            throw new BusinessException(ApiCode.BAD_REQUEST, "内置实现 " + key + " 无效");
        }
        version.setImplementationKey(builtin.getImplementationKey());
        version.setInputSchema(builtin.getInputSchema());
        version.setReadOnly(builtin.isReadOnly());
        return true;
    }

    private boolean fillHttp(ToolVersionPO version, ToolSaveDTO request, Set<String> parameterNames, boolean strict) {
        if (parameterNames.contains(HttpToolTemplate.CREDENTIAL)) {
            throw new BusinessException(ApiCode.BAD_REQUEST, "参数名 credential 保留给凭据占位符，请改用其他名称");
        }
        if (request.getHttpSpec() == null) {
            if (strict) {
                throw new BusinessException(ApiCode.BAD_REQUEST, "HTTP 工具须配置请求模板");
            }
            return false;
        }

        HttpToolSpecBO spec = converter.toSpec(request.getHttpSpec());
        spec.setUrl(trimToEmpty(spec.getUrl()));
        spec.setBodyTemplate(blankToNull(spec.getBodyTemplate()));
        spec.setCredentialRef(blankToNull(spec.getCredentialRef()));
        spec.setResultPointer(blankToNull(spec.getResultPointer()));
        if (spec.getCredentialRef() != null) {
            credentials.requireConfigured(spec.getCredentialRef());
        }
        version.setHttpSpec(spec);

        if (spec.getMethod() == null || spec.getUrl().isBlank()) {
            if (strict) {
                throw new BusinessException(ApiCode.BAD_REQUEST, "HTTP 工具须填写请求方法和地址");
            }
            return false;
        }

        List<String> errors = HttpToolTemplate.validate(spec, parameterNames);
        if (!errors.isEmpty()) {
            throw new BusinessException(ApiCode.BAD_REQUEST, String.join("；", errors));
        }
        return true;
    }

    private boolean fillMcp(ToolVersionPO version, ToolSaveDTO request, boolean strict) {
        String mcpServerIdText = blankToNull(request.getMcpServerId());
        String remoteToolName = blankToNull(request.getRemoteToolName());
        if (mcpServerIdText != null) {
            long mcpServerId = ConfigRules.parseId(mcpServerIdText, "MCP 服务ID");
            // 正向关联规则：所选 MCP 必须处于未删除、启用且配置就绪状态
            mcpServers.requireUsableServer(mcpServerId);
            version.setMcpServerId(mcpServerId);
        }
        version.setRemoteToolName(remoteToolName);

        boolean complete = version.getMcpServerId() != null && remoteToolName != null;
        if (strict && !complete) {
            throw new BusinessException(ApiCode.BAD_REQUEST, "MCP 工具须选择服务与远程工具");
        }
        return complete;
    }

    private ToolDefinitionBO definitionOf(ToolVersionPO version) {
        ToolDefinitionBO definition = new ToolDefinitionBO();
        definition.setToolType(version.getToolType());
        definition.setExposedName(version.getExposedName());
        definition.setDescription(version.getDescription());
        definition.setInputSchema(version.getInputSchema());
        definition.setReadOnly(Boolean.TRUE.equals(version.getReadOnly()));
        definition.setImplementationKey(version.getImplementationKey());
        definition.setHttpSpec(version.getHttpSpec());
        definition.setRemoteToolName(version.getRemoteToolName());

        if (version.getHttpSpec() != null) {
            definition.setCredential(credentials.resolve(version.getHttpSpec().getCredentialRef()));
        }
        if (version.getMcpServerId() != null) {
            definition.setMcpConnection(mcpServers.usableConnection(version.getMcpServerId()));
            definition.setMcpServerLabel(mcpServers.label(version.getMcpServerId()));
        }
        return definition;
    }

    private void requireNoActiveUsage(long toolId, String action) {
        List<String> owners = tools.selectUsages(toolId).stream()
                .filter(ConfigUsageBO::isActive)
                .map(usage -> usage.getOwnerName() + "（" + usage.getOwnerCode() + "）")
                .distinct()
                .toList();
        if (!owners.isEmpty()) {
            throw new BusinessException(ApiCode.CONFLICT,
                    "以下启用的 Agent 正在使用该工具，无法" + action + "：" + String.join("、", owners));
        }
    }

    private void requireNoAnyUsage(long toolId) {
        List<String> owners = tools.selectUsages(toolId).stream()
                .map(usage -> usage.getOwnerName() + "（" + usage.getOwnerCode() + "）")
                .distinct()
                .toList();
        if (!owners.isEmpty()) {
            throw new BusinessException(ApiCode.CONFLICT,
                    "以下 Agent 仍引用该工具，请先调整关联并重新发布后再删除：" + String.join("、", owners));
        }
    }

    private ToolVO summary(long toolId) {
        List<ToolSummaryBO> summaries = tools.selectSummaries(toolId);
        if (summaries.isEmpty()) {
            throw new BusinessException(ApiCode.NOT_FOUND, "工具不存在");
        }
        return converter.toView(summaries.get(0));
    }

    private ToolPO requireTool(long toolId) {
        ToolPO tool = tools.selectOne(Wrappers.<ToolPO>lambdaQuery()
                .eq(ToolPO::getToolId, toolId)
                .isNull(ToolPO::getDeletedAt));
        if (tool == null) {
            throw new BusinessException(ApiCode.NOT_FOUND, "工具不存在");
        }
        return tool;
    }

    private ToolVersionPO requireVersion(long toolId, long versionId) {
        ToolVersionPO version = versions.selectOne(Wrappers.<ToolVersionPO>lambdaQuery()
                .eq(ToolVersionPO::getToolId, toolId)
                .eq(ToolVersionPO::getToolVersionId, versionId));
        if (version == null) {
            throw new BusinessException(ApiCode.NOT_FOUND, "工具配置版本不存在");
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
