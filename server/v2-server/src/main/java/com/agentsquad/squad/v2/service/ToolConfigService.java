package com.agentsquad.squad.v2.service;

import com.agentsquad.squad.common.enums.ApiCode;
import com.agentsquad.squad.common.exceptions.BusinessException;
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
import com.agentsquad.squad.v2.dto.ToolCreateDTO;
import com.agentsquad.squad.v2.dto.ToolDraftDTO;
import com.agentsquad.squad.v2.dto.ToolProfileDTO;
import com.agentsquad.squad.v2.dto.ToolTestRunDTO;
import com.agentsquad.squad.v2.enums.ConfigStatus;
import com.agentsquad.squad.v2.enums.ToolType;
import com.agentsquad.squad.v2.enums.VersionStatus;
import com.agentsquad.squad.v2.mapper.McpServerVersionMapper;
import com.agentsquad.squad.v2.mapper.ToolMapper;
import com.agentsquad.squad.v2.mapper.ToolVersionMapper;
import com.agentsquad.squad.v2.po.McpServerVersionPO;
import com.agentsquad.squad.v2.po.ToolPO;
import com.agentsquad.squad.v2.po.ToolVersionPO;
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
import java.util.Set;

/**
 * 工具配置管理
 * <p>
 * 维护工具目录、版本、启停和试运行。工具按类型生成：内置工具引用代码实现，HTTP 工具按请求模板调用接口，
 * MCP 工具引用已发布的 MCP 服务版本。Agent 只能绑定启用工具的已发布版本，工具存在生效引用时不能停用。
 */
@Slf4j
@Service
public class ToolConfigService {
    private static final String CONFLICT = "工具或版本已被其他操作修改，请刷新后重试";

    @Resource
    private ToolMapper tools;

    @Resource
    private ToolVersionMapper versions;

    @Resource
    private McpServerVersionMapper mcpVersions;

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
     * 返回工具摘要、版本记录和绑定该工具的 Agent 版本。
     */
    public ToolDetailVO detail(long toolId) {
        ToolDetailVO detail = new ToolDetailVO();
        detail.setTool(summary(toolId));
        detail.setVersions(versions.selectSummaries(toolId).stream().map(converter::toView).toList());
        detail.setUsages(tools.selectUsages(toolId).stream().map(converter::toView).toList());
        return detail;
    }

    /**
     * 新增工具
     * <p>
     * 同一事务创建工具目录和第一个版本草稿；编码重复时返回冲突，整笔回滚。
     */
    @Transactional
    public ToolDetailVO create(ToolCreateDTO request) {
        ToolVersionPO draft = buildDraft(request.getToolType(), request.getDraft());
        long actorId = ConfigRules.actor();

        ToolPO tool = new ToolPO();
        tool.setToolId(IdWorker.getId());
        tool.setToolCode(request.getToolCode());
        tool.setToolName(request.getToolName().trim());
        tool.setToolType(request.getToolType());
        tool.setStatus(ConfigStatus.ENABLED);
        tool.setRevision(0L);
        tool.setCreatorId(actorId);
        tool.setLastUpdaterId(actorId);
        try {
            tools.insert(tool);
        } catch (DuplicateKeyException duplicated) {
            throw new BusinessException(ApiCode.CONFLICT, "工具编码 " + request.getToolCode() + " 已存在");
        }

        insertDraft(tool.getToolId(), draft, actorId);
        log.info("工具已创建，toolId={}，code={}，type={}", tool.getToolId(), tool.getToolCode(), tool.getToolType());
        return detail(tool.getToolId());
    }

    /**
     * 修改工具资料
     * <p>
     * 只修改展示名称，按目录修订号条件更新。
     */
    public ToolVO updateProfile(long toolId, ToolProfileDTO request) {
        requireTool(toolId);

        ToolPO tool = new ToolPO();
        tool.setToolId(toolId);
        tool.setToolName(request.getToolName().trim());
        tool.setRevision(request.getRevision());
        tool.setLastUpdaterId(ConfigRules.actor());
        ConfigRules.requireChanged(tools.updateProfile(tool), CONFLICT);
        return summary(toolId);
    }

    /**
     * 启用或停用工具
     * <p>
     * 停用条件更新在同一语句复核没有启用 Agent 的当前版本绑定；失败时区分“存在引用”和“修订号变化”。
     */
    public ToolVO changeStatus(long toolId, ConfigStatus status, long revision) {
        requireTool(toolId);

        ToolPO tool = new ToolPO();
        tool.setToolId(toolId);
        tool.setStatus(status);
        tool.setRevision(revision);
        tool.setLastUpdaterId(ConfigRules.actor());
        if (tools.updateStatus(tool) != 1) {
            if (status == ConfigStatus.DISABLED) {
                requireNoActiveUsage(toolId);
            }
            throw new BusinessException(ApiCode.CONFLICT, CONFLICT);
        }

        log.info("工具状态已更新，toolId={}，status={}", toolId, status);
        return summary(toolId);
    }

    /**
     * 读取版本
     * <p>
     * MCP 工具补充所引用服务的名称与版本号。
     */
    public ToolVersionVO readVersion(long toolId, long versionId) {
        ToolVersionPO version = requireVersion(toolId, versionId);
        ToolVersionVO view = converter.toView(version);
        if (version.getMcpServerVersionId() != null) {
            McpServerVersionPO mcpVersion = mcpVersions.selectOne(Wrappers.<McpServerVersionPO>lambdaQuery()
                    .eq(McpServerVersionPO::getMcpServerVersionId, version.getMcpServerVersionId()));
            view.setMcpServerName(mcpServers.label(version.getMcpServerVersionId()));
            view.setMcpServerVersionNo(mcpVersion == null ? null : mcpVersion.getVersionNo());
        }
        return view;
    }

    /**
     * 新建版本草稿
     * <p>
     * 每个工具最多一个草稿，并发新建由唯一索引拒绝。
     */
    @Transactional
    public ToolVersionVO createDraft(long toolId, ToolDraftDTO request) {
        ToolPO tool = requireTool(toolId);
        ToolVersionPO draft = buildDraft(tool.getToolType(), request);

        long versionId = insertDraft(toolId, draft, ConfigRules.actor());
        return readVersion(toolId, versionId);
    }

    /**
     * 保存版本草稿
     * <p>
     * 只更新草稿状态且修订号匹配的版本，已发布版本保持不变。
     */
    public ToolVersionVO saveDraft(long toolId, long versionId, ToolDraftDTO request) {
        if (request.getRevision() == null) {
            throw new BusinessException(ApiCode.BAD_REQUEST, "编辑草稿需要修订号");
        }
        ToolPO tool = requireTool(toolId);
        ToolVersionPO draft = buildDraft(tool.getToolType(), request);

        draft.setToolId(toolId);
        draft.setToolVersionId(versionId);
        draft.setRevision(request.getRevision());
        draft.setLastUpdaterId(ConfigRules.actor());
        ConfigRules.requireChanged(versions.updateDraft(draft), CONFLICT);
        return readVersion(toolId, versionId);
    }

    /**
     * 发布版本
     * <p>
     * 发布前要求描述已填写、实现依赖可用；MCP 工具在同一事务锁定所引用的服务，防止发布与服务停用交错。
     */
    @Transactional
    public ToolVersionVO publish(long toolId, long versionId, long revision) {
        ToolPO tool = requireTool(toolId);
        if (tool.getStatus() != ConfigStatus.ENABLED) {
            throw new BusinessException(ApiCode.CONFLICT, "停用的工具不能发布，请先启用");
        }

        ToolVersionPO version = requireVersion(toolId, versionId);
        if (version.getDescription() == null || version.getDescription().isBlank()) {
            throw new BusinessException(ApiCode.BAD_REQUEST, "发布前请填写工具描述，模型据此判断何时调用");
        }
        requirePublishableImplementation(version);

        version.setRevision(revision);
        version.setLastUpdaterId(ConfigRules.actor());
        ConfigRules.requireChanged(versions.publish(version), CONFLICT);
        log.info("工具版本已发布，toolId={}，versionNo={}", toolId, version.getVersionNo());
        return readVersion(toolId, versionId);
    }

    /**
     * 试运行工具
     * <p>
     * 按未保存的定义生成工具并调用一次，不写库；定义不合法时返回参数错误，调用失败体现在结果中。
     */
    public ToolTestRunVO testRun(ToolTestRunDTO request) {
        ToolVersionPO draft = buildDraft(request.getToolType(), request.getDraft());
        ToolDefinitionBO definition = definitionOf(draft);

        ToolRunResultBO result = toolRunner.run(definition, request.getArguments());
        return converter.toView(result);
    }

    /**
     * 列出内置实现
     */
    public List<BuiltinToolOptionVO> builtinOptions() {
        return builtinTools.list().stream().map(converter::toView).toList();
    }

    /**
     * 列出 Agent 可绑定的工具版本
     */
    public List<ToolOptionVO> usableOptions() {
        return versions.selectUsableOptions().stream().map(converter::toView).toList();
    }

    /**
     * 登记引用前锁定工具
     * <p>
     * 以条件更新确认工具仍启用并持有行锁到事务结束，与工具停用串行；MCP 工具同时锁定所引用的服务。
     */
    void lockEnabledForReference(long toolId, Long mcpServerVersionId) {
        if (tools.confirmEnabled(toolId) != 1) {
            throw new BusinessException(ApiCode.CONFLICT, "绑定的工具已停用，请刷新后重新选择");
        }
        if (mcpServerVersionId != null) {
            mcpServers.lockEnabledForReference(mcpServerVersionId);
        }
    }

    /**
     * 组装并校验草稿
     * <p>
     * 校验格式、参数结构和类型对应的实现配置；内置工具的参数与只读标记以代码实现为准。
     */
    private ToolVersionPO buildDraft(ToolType toolType, ToolDraftDTO request) {
        ToolVersionPO draft = new ToolVersionPO();
        draft.setToolType(toolType);
        draft.setExposedName(request.getExposedName());
        draft.setDescription(request.getDescription().trim());
        draft.setInputSchema(request.getInputSchema());
        draft.setReadOnly(request.isReadOnly());

        if (toolType == ToolType.BUILTIN) {
            fillBuiltin(draft, request);
        } else if (toolType == ToolType.HTTP) {
            fillHttp(draft, request);
        } else {
            fillMcp(draft, request);
        }
        ToolSchemaRules.validate(draft.getInputSchema());
        return draft;
    }

    private void fillBuiltin(ToolVersionPO draft, ToolDraftDTO request) {
        BuiltinToolBO builtin = builtinTools.find(request.getImplementationKey());
        if (builtin == null) {
            throw new BusinessException(ApiCode.BAD_REQUEST, "请选择有效的内置实现");
        }
        draft.setImplementationKey(builtin.getImplementationKey());
        draft.setInputSchema(builtin.getInputSchema());
        draft.setReadOnly(builtin.isReadOnly());
    }

    private void fillHttp(ToolVersionPO draft, ToolDraftDTO request) {
        if (request.getHttpSpec() == null) {
            throw new BusinessException(ApiCode.BAD_REQUEST, "HTTP 工具须配置请求模板");
        }
        HttpToolSpecBO spec = converter.toSpec(request.getHttpSpec());
        spec.setUrl(spec.getUrl().trim());
        spec.setBodyTemplate(blankToNull(spec.getBodyTemplate()));
        spec.setCredentialRef(blankToNull(spec.getCredentialRef()));
        spec.setResultPointer(blankToNull(spec.getResultPointer()));

        Set<String> parameterNames = ToolSchemaRules.validate(request.getInputSchema());
        if (parameterNames.contains(HttpToolTemplate.CREDENTIAL)) {
            throw new BusinessException(ApiCode.BAD_REQUEST, "参数名 credential 保留给凭据占位符，请改用其他名称");
        }
        List<String> errors = HttpToolTemplate.validate(spec, parameterNames);
        if (!errors.isEmpty()) {
            throw new BusinessException(ApiCode.BAD_REQUEST, String.join("；", errors));
        }
        credentials.requireConfigured(spec.getCredentialRef());
        draft.setHttpSpec(spec);
    }

    private void fillMcp(ToolVersionPO draft, ToolDraftDTO request) {
        String remoteToolName = blankToNull(request.getRemoteToolName());
        if (request.getMcpServerVersionId() == null || remoteToolName == null) {
            throw new BusinessException(ApiCode.BAD_REQUEST, "MCP 工具须选择服务版本和远程工具");
        }
        long mcpServerVersionId = ConfigRules.parseId(request.getMcpServerVersionId(), "MCP 服务版本ID");
        mcpServers.usableConnection(mcpServerVersionId);

        draft.setMcpServerVersionId(mcpServerVersionId);
        draft.setRemoteToolName(remoteToolName);
    }

    private void requirePublishableImplementation(ToolVersionPO version) {
        if (version.getToolType() == ToolType.BUILTIN && builtinTools.find(version.getImplementationKey()) == null) {
            throw new BusinessException(ApiCode.BAD_REQUEST, "内置实现 " + version.getImplementationKey() + " 已不存在");
        }
        if (version.getToolType() == ToolType.HTTP) {
            credentials.requireConfigured(version.getHttpSpec().getCredentialRef());
        }
        if (version.getMcpServerVersionId() != null) {
            mcpServers.lockEnabledForReference(version.getMcpServerVersionId());
        }
    }

    /**
     * 生成可执行定义
     * <p>
     * 解析凭据与 MCP 连接，返回值含密钥，只在服务端内存中使用。
     */
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
        if (version.getMcpServerVersionId() != null) {
            definition.setMcpConnection(mcpServers.usableConnection(version.getMcpServerVersionId()));
            definition.setMcpServerLabel(mcpServers.label(version.getMcpServerVersionId()));
        }
        return definition;
    }

    private long insertDraft(long toolId, ToolVersionPO draft, long actorId) {
        draft.setToolId(toolId);
        draft.setToolVersionId(IdWorker.getId());
        draft.setVersionNo(versions.nextVersionNo(toolId));
        draft.setStatus(VersionStatus.DRAFT);
        draft.setRevision(0L);
        draft.setCreatorId(actorId);
        draft.setLastUpdaterId(actorId);
        try {
            versions.insert(draft);
        } catch (DuplicateKeyException duplicated) {
            throw new BusinessException(ApiCode.CONFLICT, "该工具已有未发布的草稿，请编辑现有草稿");
        }
        return draft.getToolVersionId();
    }

    private void requireNoActiveUsage(long toolId) {
        List<String> owners = tools.selectUsages(toolId).stream()
                .filter(ConfigUsageBO::isActive)
                .map(usage -> usage.getOwnerName() + "（" + usage.getOwnerCode() + "）")
                .distinct()
                .toList();
        if (!owners.isEmpty()) {
            throw new BusinessException(ApiCode.CONFLICT, "以下启用的 Agent 正在使用该工具，请先调整其当前版本：" + String.join("、", owners));
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
        ToolPO tool = tools.selectOne(Wrappers.<ToolPO>lambdaQuery().eq(ToolPO::getToolId, toolId));
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
            throw new BusinessException(ApiCode.NOT_FOUND, "工具版本不存在");
        }
        return version;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
