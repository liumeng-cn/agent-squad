package com.agentsquad.squad.runtime.bridge;

import com.agentsquad.squad.runtime.domain.DeliveryRow;
import com.agentsquad.squad.runtime.domain.MasterAgentIdentity;
import com.agentsquad.squad.runtime.domain.ProjectionTarget;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.session.SessionEvent;
import io.agentscope.extensions.jdbc.JdbcDistributedStore;
import io.agentscope.extensions.jdbc.dialect.AbstractJdbcDialect;
import io.agentscope.extensions.model.openai.OpenAIChatModel;
import io.agentscope.harness.agent.HarnessAgent;
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

import java.util.List;
import javax.sql.DataSource;

/**
 * 当前 Master 的 AgentScope 原生会话适配器。
 *
 * <p>复用 Spring DataSource 创建官方 JDBC DistributedStore；固定 Binding 标识决定恢复哪段原生日志。</p>
 */
@Component
@DependsOnDatabaseInitialization
@ConditionalOnProperty(prefix = "agentsquad.delivery", name = "enabled", havingValue = "true")
public class AgentScopeSessionGateway implements NativeSessionGateway {

    @Resource
    private DataSource dataSource;

    @Value("${agentsquad.model.api-key:}")
    private String apiKey;

    @Value("${agentsquad.model.base-url:}")
    private String baseUrl;

    @Value("${agentsquad.model.name:}")
    private String modelName;

    private HarnessAgent master;

    /** 启用投递前检查模型配置和 Flyway 表结构，再创建长生命周期 Master。 */
    @PostConstruct
    public void initialize() {
        if (apiKey.isBlank() || baseUrl.isBlank() || modelName.isBlank()) {
            throw new IllegalStateException("已启用命令投递，但模型 API Key、地址或名称未配置");
        }
        AbstractJdbcDialect dialect = AbstractJdbcDialect.from(dataSource)
                .autoCreateTable(false)
                .build();
        master = HarnessAgent.builder()
                .agentId(MasterAgentIdentity.AGENT_ID)
                .name("Squad Master")
                .sysPrompt("你是多智能体平台的 Master。当前阶段只回答用户问题，不调用工具或执行外部操作。")
                .model(OpenAIChatModel.builder().apiKey(apiKey).baseUrl(baseUrl)
                        .modelName(modelName).build())
                .distributedStore(JdbcDistributedStore.create(dataSource, dialect))
                .filesystem(new RemoteFilesystemSpec().isolationScope(IsolationScope.USER))
                .disableSubagents()
                .disableFilesystemTools()
                .disableShellTool()
                .disableWebTools()
                .disableMemoryTools()
                .disableMemoryHooks()
                .disableDynamicSkills()
                .disableDefaultWorkspaceSkills()
                .build();
    }

    /**
     * 用绑定的用户/会话身份和原命令 ID 提交，重复投递不会产生第二个原生回合。
     */
    @Override
    public NativeSubmissionReceipt submit(DeliveryRow delivery, String message) {
        if (!MasterAgentIdentity.AGENT_ID.equals(delivery.agentId())) {
            throw new IllegalStateException("未注册的 Master 能力版本");
        }
        RuntimeContext context = RuntimeContext.builder()
                .userId(delivery.userId())
                .sessionId(delivery.sessionId())
                .build();
        AgentSession.Task receipt = master.session(context).submit(delivery.commandId(), message);
        return new NativeSubmissionReceipt(receipt.commandId(), receipt.turnId(),
                receipt.runId(), receipt.status());
    }

    /**
     * 读取固定会话的已提交原生前缀，并在重启后重新启动其待执行命令调度。
     */
    @Override
    public List<NativeEventView> readEvents(ProjectionTarget target, long after, int limit) {
        if (!MasterAgentIdentity.AGENT_ID.equals(target.agentId())) {
            throw new IllegalStateException("未注册的 Master 能力版本");
        }
        RuntimeContext context = RuntimeContext.builder()
                .userId(target.userId())
                .sessionId(target.sessionId())
                .build();
        AgentSession session = master.session(context);
        session.start();
        return session.log().readAfter(after, limit).stream()
                .map(this::toEventView)
                .toList();
    }

    private NativeEventView toEventView(SessionEvent event) {
        Object status = "run/end".equals(event.type()) ? event.data().get("status") : null;
        return new NativeEventView(event.seq(), event.eventId(), event.type(),
                event.executionRunId(), event.turnId(), status == null ? null : status.toString(),
                event.occurredAt());
    }

    /**
     * 关闭本进程持有的原生会话调度器；日志保留在 MySQL。
     */
    @PreDestroy
    public void close() {
        if (master != null) {
            master.close();
        }
    }
}
