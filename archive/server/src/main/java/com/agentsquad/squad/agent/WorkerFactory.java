package com.agentsquad.squad.agent;


import com.agentsquad.squad.agent.session.RuntimeSessionProvider;
import com.agentsquad.squad.agent.skill.ScopeSkillCapabilities;
import com.agentsquad.squad.agent.skill.ScopeSkillRepository;
import com.agentsquad.squad.agent.tool.CommitArtifactTool;
import com.agentsquad.squad.agent.tool.SandboxPythonTool;
import com.agentsquad.squad.observer.RuntimeEventSinkFactory;
import com.agentsquad.squad.service.ModelCatalogService;
import com.agentsquad.squad.agent.middleware.ExecutionContextMiddleware;
import com.agentsquad.squad.agent.skill.PlatformSkillVisibility;
import com.agentsquad.squad.model.bo.WorkerAgentHandleBO;
import com.agentsquad.squad.model.bo.RuntimeEventSourceBO;
import com.agentsquad.squad.model.bo.SkillSelectionBO;
import com.agentsquad.squad.service.LocalSessionStopper;
import com.agentsquad.squad.service.RuntimeWorkspace;

import com.agentsquad.squad.agent.tool.ReportJobResultTool;
import com.agentsquad.squad.utils.CanonicalJson;
import com.agentsquad.squad.utils.ContentDigests;
import com.agentsquad.squad.utils.RuntimePayloads;
import com.agentsquad.squad.model.bo.WorkerExecutionIdentityBO;
import com.agentsquad.squad.agent.middleware.ModelBudgetMiddleware;
import com.agentsquad.squad.agent.middleware.ProfiledModelBudgetMiddleware;
import com.agentsquad.squad.model.po.ContextSnapshotPO;
import com.agentsquad.squad.model.bo.WorkerLaunchBO;
import com.agentsquad.squad.mapper.ContextSnapshotMapper;
import com.agentsquad.squad.mapper.WorkerDispatchMapper;
import com.agentsquad.squad.service.WorkerDispatchService;
import com.agentsquad.squad.service.WorkerResultService;
import com.agentsquad.squad.service.WorkerDefinitionService;
import com.agentsquad.squad.model.vo.WorkerDefinitionVO;
import com.agentsquad.squad.config.properties.WorkerModelProfileProperties;
import com.agentsquad.squad.config.properties.WorkerProfileProperties;
import com.agentsquad.squad.config.properties.WorkerRuntimeProfileProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.model.ExecutionConfig;
import io.agentscope.core.model.GenerateOptions;
import io.agentscope.core.model.ToolChoice;
import io.agentscope.core.tool.Toolkit;
import io.agentscope.extensions.jdbc.JdbcDistributedStore;
import io.agentscope.extensions.jdbc.dialect.AbstractJdbcDialect;
import io.agentscope.extensions.model.openai.OpenAIChatModel;
import io.agentscope.harness.agent.HarnessAgent;
import io.agentscope.harness.agent.tools.ToolsConfig;
import io.agentscope.harness.agent.IsolationScope;
import io.agentscope.harness.agent.filesystem.spec.RemoteFilesystemSpec;
import io.agentscope.harness.agent.session.AgentSession;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.annotation.Resource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.sql.init.dependency.DependsOnDatabaseInitialization;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 按持久 WorkerDefinition 构建固定能力版本的叶子 HarnessAgent。
 *
 * <p>Definition 只提供能力契约；实际模型密钥与运行上限从服务端 Profile 解析。
 * 未注册工具显式拒绝，不能通过 Definition 扩大 Master 权限。</p>
 */
@Component
@DependsOnDatabaseInitialization
@ConditionalOnProperty(prefix = "agentsquad.delivery", name = "enabled", havingValue = "true")
public class WorkerFactory {

    @Resource
    private RuntimeEventSinkFactory eventSinks;

    @Resource
    private DataSource dataSource;

    @Resource
    private WorkerDispatchMapper dispatchMapper;

    @Resource
    private WorkerDispatchService dispatchService;

    @Resource
    private WorkerResultService resultService;

    @Resource
    private WorkerDefinitionService definitionService;

    @Resource
    private ContextSnapshotMapper snapshotMapper;

    @Resource
    private WorkerProfileProperties profileRegistry;

    @Resource
    private ModelBudgetMiddleware budgetMiddleware;

    @Resource
    private ObjectMapper objectMapper;

    @Resource
    private ScopeSkillRepository scopeSkillRepository;

    @Resource
    private PlatformSkillVisibility skillVisibility;

    @Resource
    private ExecutionContextMiddleware executionContext;

    @Resource
    private SandboxPythonTool sandboxPython;

    @Resource
    private CommitArtifactTool commitArtifact;

    @Value("${agentsquad.sandbox.enabled:false}")
    private boolean sandboxEnabled;

    @Resource
    private RuntimeWorkspace runtimeWorkspace;

    private final Map<Long, WorkerAgentHandleBO> active = new ConcurrentHashMap<>();

    @Resource
    private ModelCatalogService modelCatalog;

    @Resource
    private LocalSessionStopper localSessions;

    @Resource
    private RuntimeSessionProvider runtimeSessions;

    private AbstractJdbcDialect dialect;

    /**
     * 完成或失效的 Worker 不再参与投影，定期释放本进程 Agent 资源。
     */
    public void pruneFinished() {
        for (WorkerAgentHandleBO handle : active.values()) {
            WorkerLaunchBO job = dispatchMapper.findByBinding(handle.userId(), handle.bindingId());
            if (job != null && java.util.Set.of("READY", "RUNNING", "WAITING_INPUT",
                    "WAITING_APPROVAL", "RECONCILING", "CANCELLING").contains(job.getJobStatus())) {
                continue;
            }
            if (active.remove(handle.bindingId(), handle)) {
                handle.agent().close();
            }
        }
    }

    @PostConstruct
    public void initialize() {
        dialect = AbstractJdbcDialect.from(dataSource).autoCreateTable(false).build();
    }

    /**
     * 取得固定 Binding 的本进程 Agent；首次执行先 CAS 领取业务 guard。
     */
    public AgentSession sessionFor(Long userId, Long bindingId, String sessionId, String agentId) {
        WorkerAgentHandleBO handle = active.computeIfAbsent(bindingId,
                ignored -> create(userId, bindingId, sessionId, agentId));
        if (!userId.equals(handle.userId()) || !sessionId.equals(handle.sessionId())
                || !agentId.equals(handle.agentId())) {
            throw new IllegalStateException("Worker Binding 身份不一致");
        }
        dispatchService.renew(userId, bindingId, handle.guardToken());
        RuntimeContext context = RuntimeContext.builder().userId(String.valueOf(userId)).sessionId(sessionId).build();
        context.put(io.agentscope.core.session.SessionExportSink.CONTEXT_KEY,
                eventSinks.bind(new RuntimeEventSourceBO(
                        userId, bindingId, sessionId,
                        RuntimeEventSourceBO.CURRENT_MAPPING_VERSION)));

        context.put(WorkerExecutionIdentityBO.class,
                new WorkerExecutionIdentityBO(bindingId, handle.guardToken()));
        WorkerLaunchBO job = dispatchMapper.findByBinding(userId, bindingId);
        JsonNode selectedInput = readInput(job.getInputJson());
        ContextSnapshotPO selected = snapshotMapper.findOwned(userId, job.getTaskId(), Long.parseLong(selectedInput.path("contextSnapshotId").asText()));
        java.util.List<Long> ids = new java.util.ArrayList<>();
        for (JsonNode item : readInput(selected.getItemsJson())) {
            if ("SKILL_REFERENCE".equals(item.path("kind").asText())) {
                ids.add(Long.parseLong(item.path("sourceId").asText()));
            }
        }
        context.put(SkillSelectionBO.class,
                new SkillSelectionBO(ids));
        var allowedTools = new java.util.HashSet<>(definitionService.requirePublished(job.getWorkerKey(), job.getWorkerVersion()).toolAllowlist());
        allowedTools.add("load_skill_through_path");
        allowedTools.add("report_job_result");
        context.put(ScopeSkillCapabilities.class,
                new ScopeSkillCapabilities(allowedTools));
        return localSessions.register(userId, bindingId, handle.agent().session(context));
    }

    private JsonNode readInput(String value) {
        try {
            return RuntimePayloads.readJson(objectMapper, value);
        } catch (Exception exception) {
            throw new IllegalStateException("Worker固定资料无法解析", exception);
        }
    }

    private WorkerAgentHandleBO create(Long userId, Long bindingId,
                                       String sessionId, String agentId) {
        WorkerLaunchBO job = dispatchMapper.findByBinding(userId, bindingId);
        if (job == null || !"ACTIVE".equals(job.getPlanStatus())
                || job.getCancelRequestedAt() != null) {
            throw new IllegalStateException("Worker Binding 不可执行");
        }
        WorkerDefinitionVO definition = "READY".equals(job.getJobStatus())
                ? definitionService.requireEnabled(job.getWorkerKey(), job.getWorkerVersion())
                : definitionService.requirePublished(job.getWorkerKey(), job.getWorkerVersion());
        String expectedAgentId = ContentDigests.sha256(String.valueOf(definition.definitionId())).substring(0, 32);
        if (!expectedAgentId.equals(agentId)) {
            throw new IllegalStateException("Worker 固定能力版本与 Binding 不一致");
        }
        if (!java.util.Set.of("sandbox_python", "commit_artifact").containsAll(definition.toolAllowlist())
                || !sandboxEnabled && !definition.toolAllowlist().isEmpty()) {
            throw new IllegalStateException("Worker Definition 引用了当前未注册的受控工具");
        }
        WorkerModelProfileProperties model = profileRegistry.requireExecutableModel(definition.modelProfileKey());
        WorkerRuntimeProfileProperties runtime = profileRegistry.requireRuntime(definition.runtimeProfileKey());
        JsonNode input;
        try {
            input = RuntimePayloads.readJson(objectMapper, job.getInputJson());
        } catch (Exception exception) {
            throw new IllegalStateException("Worker 固定输入无法读取", exception);
        }
        JsonNode snapshotNode = input.path("contextSnapshotId");
        Long snapshotId = snapshotNode.isMissingNode() || snapshotNode.isNull() ? null : snapshotNode.asLong();
        ContextSnapshotPO snapshot = snapshotMapper.findOwned(userId, job.getTaskId(), snapshotId);
        if (snapshot == null || !job.getNodeKey().equals(snapshot.getTargetNodeKey())
                || !CanonicalJson.sha256(objectMapper, snapshot.getItemsJson()).equals(snapshot.getContentHash())) {
            throw new IllegalStateException("Worker 局部上下文已失效");
        }
        WorkerLaunchBO claimed = dispatchService.claim(userId, bindingId);
        Toolkit toolkit = new Toolkit();
        if (definition.toolAllowlist().contains("sandbox_python")) {
            toolkit.registerAgentTool(sandboxPython);
        }
        if (definition.toolAllowlist().contains("commit_artifact")) {
            toolkit.registerAgentTool(commitArtifact);
        }
        toolkit.registerAgentTool(new ReportJobResultTool(resultService, objectMapper,
                userId, sessionId, bindingId, claimed.getGuardToken()).outputSchema(definition.outputSchema()));
        boolean hasSelectedSkills = readInput(snapshot.getItemsJson()).valueStream()
                .anyMatch(item -> "SKILL_REFERENCE".equals(item.path("kind").asText()));
        String prompt = "你是独立 Worker。能力：" + definition.description()
                + "。只处理当前节点，不发布计划、不结束父目标、不委派其他 Agent。"
                + "结果是供统一助手核验的材料，不是直接发给用户的消息。提供实质内容、依据与具体缺口，"
                + "区分事实和推断，不写领取任务、工具调用、向 Master 汇报等执行叙述，不直接对用户提问。"
                + "任务说明需按已发布输入 Schema 理解：" + definition.inputSchema()
                + "。局部上下文（JSON）：" + snapshot.getItemsJson()
                + "。完成工作后必须调用 report_job_result，result 须符合已发布输出 Schema："
                + definition.outputSchema()
                + (hasSelectedSkills ? "。只使用当前available_skills列出的技能，不猜测技能ID。"
                : "。本节点未分配技能，直接根据任务说明完成工作，不尝试加载技能或工作区说明。");
        ToolsConfig toolsConfig = new ToolsConfig();
        toolsConfig.setDeny(hasSelectedSkills ? List.of("wait_async_results")
                : List.of("wait_async_results", "load_skill_through_path"));
        var generation = GenerateOptions.builder().maxTokens(model.getMaxOutputTokens());
        // 纯生成节点只有结果提交能力，必须返回结构化结果，避免正文已生成但平台无法验收。
        if (!hasSelectedSkills && definition.toolAllowlist().isEmpty()) {
            generation.toolChoice(new ToolChoice.Specific("report_job_result"));
        }
        HarnessAgent.Builder agentBuilder = HarnessAgent.builder()
                .agentId(agentId)
                .name("Worker " + definition.workerKey() + " " + definition.workerVersion())
                .sysPrompt(prompt)
                .model(OpenAIChatModel.builder().apiKey(model.getApiKey())
                        .baseUrl(model.getBaseUrl()).modelName(model.getModelName())
                        .stream(false).build())
                .generateOptions(modelCatalog.applyReasoning(generation,
                        model, model.getDefaultReasoningMode()).build())
                .modelExecutionConfig(ExecutionConfig.builder()
                        .timeout(Duration.ofSeconds(runtime.getModelRequestTimeoutSeconds()))
                        .maxAttempts(1).build())
                .toolExecutionConfig(ExecutionConfig.builder()
                        .timeout(Duration.ofSeconds(runtime.getToolTimeoutSeconds()))
                        .maxAttempts(1).build())
                .maxIters(runtime.getMaxModelCalls())
                .middleware(executionContext)
                .skillRepository(scopeSkillRepository)
                .enableSkillPromotionGate(null, skillVisibility)
                .middleware(new ProfiledModelBudgetMiddleware(
                        budgetMiddleware, definition.modelProfileKey()))
                .toolkit(toolkit)
                .distributedStore(JdbcDistributedStore.create(dataSource, dialect))
                .sessionLogStore(runtimeSessions.logStore())
                .filesystem(new RemoteFilesystemSpec().isolationScope(IsolationScope.USER))
                .toolsConfig(toolsConfig)
                .disableSubagents()
                .disableFilesystemTools()
                .disableShellTool()
                .disableWebTools()
                .disableMemoryTools()
                .disableMemoryHooks()
                .disableDefaultWorkspaceSkills();
        agentBuilder.workspace(runtimeWorkspace.path());
        HarnessAgent agent = agentBuilder.build();
        return new WorkerAgentHandleBO(agent, userId, bindingId, sessionId, agentId, claimed.getGuardToken());
    }

    /**
     * 进程退出时关闭本地 Agent；持久 Journal 和固定 Binding 保留供接管。
     */
    @PreDestroy
    public void close() {
        for (WorkerAgentHandleBO handle : active.values()) {
            handle.agent().close();
        }
        active.clear();
    }
}
