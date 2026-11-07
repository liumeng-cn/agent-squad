package com.agentsquad.squad.integration.agentscope;

import com.agentsquad.squad.agent.tool.ReportJobResultTool;
import com.agentsquad.squad.common.util.CanonicalJson;
import com.agentsquad.squad.common.util.ContentDigests;
import com.agentsquad.squad.common.util.RuntimePayloads;
import com.agentsquad.squad.domain.model.WorkerExecutionIdentity;
import com.agentsquad.squad.integration.agentscope.middleware.ModelBudgetMiddleware;
import com.agentsquad.squad.integration.agentscope.middleware.ProfiledModelBudgetMiddleware;
import com.agentsquad.squad.domain.entity.ContextSnapshotDO;
import com.agentsquad.squad.domain.entity.WorkerLaunchDO;
import com.agentsquad.squad.runtime.repository.ContextSnapshotMapper;
import com.agentsquad.squad.runtime.repository.WorkerDispatchMapper;
import com.agentsquad.squad.service.WorkerDispatchService;
import com.agentsquad.squad.service.WorkerResultService;
import com.agentsquad.squad.service.WorkerDefinitionService;
import com.agentsquad.squad.domain.dto.WorkerDefinitionResponse;
import com.agentsquad.squad.worker.profile.WorkerModelProfile;
import com.agentsquad.squad.worker.profile.WorkerProfileRegistry;
import com.agentsquad.squad.worker.profile.WorkerRuntimeProfile;
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
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.sql.init.dependency.DependsOnDatabaseInitialization;
import org.springframework.stereotype.Component;
import org.springframework.scheduling.annotation.Scheduled;

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
    private com.agentsquad.squad.integration.agentscope.inbox.RuntimeEventSinkFactory eventSinks;

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
    private WorkerProfileRegistry profileRegistry;

    @Resource
    private ModelBudgetMiddleware budgetMiddleware;

    @Resource
    private ObjectMapper objectMapper;

    @Resource
    private com.agentsquad.squad.skill.integration.ScopeSkillRepository scopeSkillRepository;

    @Resource
    private PlatformSkillVisibility skillVisibility;

    @Resource
    private ExecutionContextMiddleware executionContext;

    @Resource
    private com.agentsquad.squad.agent.tool.SandboxPythonTool sandboxPython;

    @Resource
    private com.agentsquad.squad.agent.tool.CommitArtifactTool commitArtifact;

    @org.springframework.beans.factory.annotation.Value("${agentsquad.sandbox.enabled:false}")
    private boolean sandboxEnabled;

    @Resource
    private RuntimeWorkspace runtimeWorkspace;

    private final Map<String, WorkerAgentHandle> active = new ConcurrentHashMap<>();

    @Resource
    private com.agentsquad.squad.service.ModelCatalogService modelCatalog;

    @Resource
    private LocalSessionStopper localSessions;

    @Resource
    private com.agentsquad.squad.integration.agentscope.journal.RuntimeSessionProvider runtimeSessions;

    private AbstractJdbcDialect dialect;

    /**
     * 完成或失效的 Worker 不再参与投影，定期释放本进程 Agent 资源。
     */
    @Scheduled(fixedDelayString = "${agentsquad.worker.prune-delay-ms:60000}")
    public void pruneFinished() {
        for (WorkerAgentHandle handle : active.values()) {
            WorkerLaunchDO job = dispatchMapper.findByBinding(handle.userId(), handle.bindingId());
            if (job != null && java.util.Set.of("READY", "RUNNING", "WAITING_INPUT",
                    "WAITING_APPROVAL", "RECONCILING", "CANCELLING").contains(job.jobStatus())) {
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
    public AgentSession sessionFor(String userId, String bindingId, String sessionId, String agentId) {
        WorkerAgentHandle handle = active.computeIfAbsent(bindingId,
                ignored -> create(userId, bindingId, sessionId, agentId));
        if (!userId.equals(handle.userId()) || !sessionId.equals(handle.sessionId())
                || !agentId.equals(handle.agentId())) {
            throw new IllegalStateException("Worker Binding 身份不一致");
        }
        dispatchService.renew(userId, bindingId, handle.guardToken());
        RuntimeContext context = RuntimeContext.builder().userId(userId).sessionId(sessionId).build();
        context.put(io.agentscope.core.session.SessionExportSink.CONTEXT_KEY,
                eventSinks.bind(new com.agentsquad.squad.integration.agentscope.inbox.RuntimeEventSource(
                        userId, bindingId, sessionId,
                        com.agentsquad.squad.integration.agentscope.inbox.RuntimeEventSource.CURRENT_MAPPING_VERSION)));

        context.put(WorkerExecutionIdentity.class,
                new WorkerExecutionIdentity(bindingId, handle.guardToken()));
        WorkerLaunchDO job = dispatchMapper.findByBinding(userId, bindingId);
        JsonNode selectedInput = readInput(job.inputJson());
        ContextSnapshotDO selected = snapshotMapper.findOwned(userId, job.taskId(), selectedInput.path("contextSnapshotId").asText());
        java.util.List<String> ids = new java.util.ArrayList<>();
        for (JsonNode item : readInput(selected.itemsJson())) {
            if ("SKILL_REFERENCE".equals(item.path("kind").asText())) {
                ids.add(item.path("sourceId").asText());
            }
        }
        context.put(com.agentsquad.squad.skill.integration.SkillSelection.class,
                new com.agentsquad.squad.skill.integration.SkillSelection(ids));
        var allowedTools = new java.util.HashSet<>(definitionService.requirePublished(job.workerKey(), job.workerVersion()).toolAllowlist());
        allowedTools.add("load_skill_through_path");
        allowedTools.add("report_job_result");
        context.put(com.agentsquad.squad.skill.integration.ScopeSkillCapabilities.class,
                new com.agentsquad.squad.skill.integration.ScopeSkillCapabilities(allowedTools));
        return localSessions.register(userId, bindingId, handle.agent().session(context));
    }

    private JsonNode readInput(String value) {
        try {
            return RuntimePayloads.readJson(objectMapper, value);
        } catch (Exception exception) {
            throw new IllegalStateException("Worker固定资料无法解析", exception);
        }
    }

    private WorkerAgentHandle create(String userId, String bindingId,
                                     String sessionId, String agentId) {
        WorkerLaunchDO job = dispatchMapper.findByBinding(userId, bindingId);
        if (job == null || !"ACTIVE".equals(job.planStatus())
                || job.cancelRequestedAt() != null) {
            throw new IllegalStateException("Worker Binding 不可执行");
        }
        WorkerDefinitionResponse definition = "READY".equals(job.jobStatus())
                ? definitionService.requireEnabled(job.workerKey(), job.workerVersion())
                : definitionService.requirePublished(job.workerKey(), job.workerVersion());
        String expectedAgentId = ContentDigests.sha256(definition.definitionId()).substring(0, 32);
        if (!expectedAgentId.equals(agentId)) {
            throw new IllegalStateException("Worker 固定能力版本与 Binding 不一致");
        }
        if (!java.util.Set.of("sandbox_python", "commit_artifact").containsAll(definition.toolAllowlist())
                || !sandboxEnabled && !definition.toolAllowlist().isEmpty()) {
            throw new IllegalStateException("Worker Definition 引用了当前未注册的受控工具");
        }
        WorkerModelProfile model = profileRegistry.requireExecutableModel(definition.modelProfileKey());
        WorkerRuntimeProfile runtime = profileRegistry.requireRuntime(definition.runtimeProfileKey());
        JsonNode input;
        try {
            input = RuntimePayloads.readJson(objectMapper, job.inputJson());
        } catch (Exception exception) {
            throw new IllegalStateException("Worker 固定输入无法读取", exception);
        }
        String snapshotId = input.path("contextSnapshotId").asText(null);
        ContextSnapshotDO snapshot = snapshotMapper.findOwned(userId, job.taskId(), snapshotId);
        if (snapshot == null || !job.nodeKey().equals(snapshot.targetNodeKey())
                || !CanonicalJson.sha256(objectMapper, snapshot.itemsJson()).equals(snapshot.contentHash())) {
            throw new IllegalStateException("Worker 局部上下文已失效");
        }
        WorkerLaunchDO claimed = dispatchService.claim(userId, bindingId);
        Toolkit toolkit = new Toolkit();
        if (definition.toolAllowlist().contains("sandbox_python")) {
            toolkit.registerAgentTool(sandboxPython);
        }
        if (definition.toolAllowlist().contains("commit_artifact")) {
            toolkit.registerAgentTool(commitArtifact);
        }
        toolkit.registerAgentTool(new ReportJobResultTool(resultService, objectMapper,
                userId, sessionId, bindingId, claimed.guardToken()).outputSchema(definition.outputSchema()));
        boolean hasSelectedSkills = readInput(snapshot.itemsJson()).valueStream()
                .anyMatch(item -> "SKILL_REFERENCE".equals(item.path("kind").asText()));
        String prompt = "你是独立 Worker。能力：" + definition.description()
                + "。只处理当前节点，不发布计划、不结束父目标、不委派其他 Agent。"
                + "结果是供统一助手核验的材料，不是直接发给用户的消息。提供实质内容、依据与具体缺口，"
                + "区分事实和推断，不写领取任务、工具调用、向 Master 汇报等执行叙述，不直接对用户提问。"
                + "任务说明需按已发布输入 Schema 理解：" + definition.inputSchema()
                + "。局部上下文（JSON）：" + snapshot.itemsJson()
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
        return new WorkerAgentHandle(agent, userId, bindingId, sessionId, agentId, claimed.guardToken());
    }

    /**
     * 进程退出时关闭本地 Agent；持久 Journal 和固定 Binding 保留供接管。
     */
    @PreDestroy
    public void close() {
        for (WorkerAgentHandle handle : active.values()) {
            handle.agent().close();
        }
        active.clear();
    }
}
