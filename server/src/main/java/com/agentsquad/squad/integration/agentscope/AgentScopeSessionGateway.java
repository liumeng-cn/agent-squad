package com.agentsquad.squad.integration.agentscope;

import com.agentsquad.squad.conversation.domain.DeliveryRow;
import com.agentsquad.squad.runtime.domain.MasterAgentIdentity;
import com.agentsquad.squad.runtime.domain.ProjectionTarget;
import com.agentsquad.squad.tools.mock.MockLookupTool;
import com.agentsquad.squad.tools.mock.MockTicketCreateTool;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.session.SessionEvent;
import io.agentscope.core.tool.Toolkit;
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

import javax.sql.DataSource;
import java.util.List;

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

    @Resource
    private ProposeCompletionTool proposeCompletionTool;

    @Resource
    private MockLookupTool mockLookupTool;

    @Resource
    private MockTicketCreateTool mockTicketCreateTool;

    @Resource
    private RequestInputTool requestInputTool;

    @Value("${agentsquad.model.api-key:}")
    private String apiKey;

    @Value("${agentsquad.model.base-url:}")
    private String baseUrl;

    @Value("${agentsquad.model.name:}")
    private String modelName;

    private HarnessAgent master;

    /**
     * 启用投递前检查模型配置和 Flyway 表结构，再创建长生命周期 Master。
     */
    @PostConstruct
    public void initialize() {
        if (apiKey.isBlank() || baseUrl.isBlank() || modelName.isBlank()) {
            throw new IllegalStateException("已启用命令投递，但模型 API Key、地址或名称未配置");
        }
        AbstractJdbcDialect dialect = AbstractJdbcDialect.from(dataSource)
                .autoCreateTable(false)
                .build();
        Toolkit toolkit = new Toolkit();
        toolkit.registerAgentTool(proposeCompletionTool);
        toolkit.registerAgentTool(mockLookupTool);
        toolkit.registerAgentTool(mockTicketCreateTool);
        toolkit.registerAgentTool(requestInputTool);
        master = HarnessAgent.builder()
                .agentId(MasterAgentIdentity.AGENT_ID)
                .name("Squad Master")
                .sysPrompt("你是多智能体平台的 Master。当前可用的平台工具："
                        + "mock_lookup 用于只读查询；mock_ticket_create 用于创建工单，"
                        + "它需要用户在页面审批，调用后回合会挂起等待，不要重复提交，也不要伪造审批结果；"
                        + "request_input 用于向用户追问补充信息，一次只提一个问题，不要假设用户已经回答。"
                        + "所有写入必须带稳定的 intentKey，同业务重试复用同一 intentKey。"
                        + "在回答目标之前，必须调用 propose_completion 提交 outcome 和完整 finalAnswer；"
                        + "工具回执不是用户答案。无法回答时明确说明缺少的信息。")
                .model(OpenAIChatModel.builder().apiKey(apiKey).baseUrl(baseUrl)
                        .modelName(modelName).build())
                .toolkit(toolkit)
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
        AgentSession.Task receipt = sessionFor(delivery).submit(delivery.commandId(), message);
        return new NativeSubmissionReceipt(receipt.commandId(), receipt.turnId(),
                receipt.runId(), receipt.status());
    }

    /**
     * 提交中断意图；false 表示原生会话当前没有活跃运行，平台须就地收尾。
     */
    @Override
    public boolean stop(DeliveryRow delivery) {
        AgentSession session = sessionFor(delivery);
        session.start();
        return session.interrupt(null);
    }

    /**
     * 恢复指定原生回合；SDK 拒绝时抛出 IllegalStateException，由投递器分类处理。
     */
    @Override
    public void resume(DeliveryRow delivery, String turnId) {
        AgentSession session = sessionFor(delivery);
        session.start();
        session.resume(turnId);
    }

    /**
     * 把用户答复注入挂起的外部执行请求；请求已消失按终局受理处理，会话非空闲则重试。
     */
    @Override
    public String respond(DeliveryRow delivery, String requestId, String answerText) {
        AgentSession session = sessionFor(delivery);
        session.start();
        try {
            session.respond(requestId, answerText);
        } catch (IllegalArgumentException exception) {
            String message = exception.getMessage() == null ? "" : exception.getMessage();
            if (message.contains("No pending interaction")) {
                return "no-pending-request";
            }
            throw new IllegalStateException("原生答复请求无效: " + message, exception);
        }
        return "responded";
    }

    private AgentSession sessionFor(DeliveryRow delivery) {
        if (!MasterAgentIdentity.AGENT_ID.equals(delivery.agentId())) {
            throw new IllegalStateException("未注册的 Master 能力版本");
        }
        RuntimeContext context = RuntimeContext.builder()
                .userId(delivery.userId())
                .sessionId(delivery.sessionId())
                .build();
        return master.session(context);
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
                event.occurredAt(), event.data());
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
