package com.agentsquad.squad.v2.stream;

import com.agentsquad.squad.common.model.SquadAgentRef;
import com.agentsquad.squad.common.model.SquadEventRelation;
import com.agentsquad.squad.common.model.SquadExecutionRef;
import com.agentsquad.squad.common.model.SquadMsg;
import com.agentsquad.squad.enums.SquadAgentType;
import com.agentsquad.squad.enums.SquadEventCategory;
import jakarta.annotation.PreDestroy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 只用于页面联调的模拟事件发射器。
 * 事件通过真实 HTTP SSE 推送，数据结构与正式 SquadMsg 一致，不调用模型或 AgentScope。
 */
@Service
@Profile("local")
public class TestEventStreamService {

    private final ExecutorService executor = Executors.newCachedThreadPool(runnable -> {
        Thread thread = new Thread(runnable, "squad-test-event-stream");
        thread.setDaemon(true);
        return thread;
    });

    public SseEmitter stream(TestEventStreamRequest request) {
        int interval = request.normalizedIntervalMillis();
        List<SquadMsg> events = buildParallelScenario();
        SseEmitter emitter = new SseEmitter(120_000L);

        executor.execute(() -> {
            try {
                for (SquadMsg event : events) {
                    emitter.send(SseEmitter.event()
                            .id(String.valueOf(event.getSequence()))
                            .name("squad")
                            .data(event));
                    if (interval > 0) {
                        Thread.sleep(interval);
                    }
                }
                // SSE 流正常结束即代表本次测试完成，不额外发送非 SquadMsg 控制事件。
                emitter.complete();
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                emitter.complete();
            } catch (IOException | IllegalStateException disconnected) {
                emitter.complete();
            }
        });
        return emitter;
    }

    private List<SquadMsg> buildParallelScenario() {
        List<SquadMsg> events = new ArrayList<>();
        String conversationId = "conversation-test-001";
        String turnId = "turn-test-001";

        SquadAgentRef master = agent("master", "master-instance", "主协调员", SquadAgentType.MASTER, null, 0);
        SquadAgentRef researcher = agent("researcher", "researcher-instance", "资料整理员",
                SquadAgentType.SUBAGENT, master.getAgentInstanceId(), 1);
        SquadAgentRef calculator = agent("calculator", "calculator-instance", "计算员",
                SquadAgentType.SUBAGENT, master.getAgentInstanceId(), 1);
        SquadAgentRef writer = agent("writer", "writer-instance", "汇总员",
                SquadAgentType.SUBAGENT, master.getAgentInstanceId(), 1);

        SquadExecutionRef masterRun = execution("master", "session-master", turnId, "run-master");
        SquadExecutionRef researcherRun = execution("researcher", "session-researcher", turnId, "run-researcher");
        SquadExecutionRef calculatorRun = execution("calculator", "session-calculator", turnId, "run-calculator");
        SquadExecutionRef writerRun = execution("writer", "session-writer", turnId, "run-writer");

        add(events, conversationId, turnId, "AGENT_START", SquadEventCategory.LIFECYCLE,
                master, masterRun, null, data("name", "主协调员"));
        add(events, conversationId, turnId, "MODEL_CALL_START", SquadEventCategory.MODEL,
                master, masterRun, null, data("model", "测试模型"));
        add(events, conversationId, turnId, "TEXT_BLOCK_DELTA", SquadEventCategory.MESSAGE,
                master, masterRun, null, data("blockId", "master-plan", "delta", "我将任务拆分为三个并行子任务。"));

        add(events, conversationId, turnId, "SUBAGENT_EXPOSED", SquadEventCategory.SUBAGENT,
                master, masterRun, relation(null, researcher.getAgentInstanceId()),
                data("childAgent", researcher));
        add(events, conversationId, turnId, "SUBAGENT_EXPOSED", SquadEventCategory.SUBAGENT,
                master, masterRun, relation(null, calculator.getAgentInstanceId()),
                data("childAgent", calculator));
        add(events, conversationId, turnId, "SUBAGENT_EXPOSED", SquadEventCategory.SUBAGENT,
                master, masterRun, relation(null, writer.getAgentInstanceId()),
                data("childAgent", writer));

        add(events, conversationId, turnId, "AGENT_START", SquadEventCategory.LIFECYCLE,
                researcher, researcherRun, null, data("name", "资料整理员"));
        add(events, conversationId, turnId, "AGENT_START", SquadEventCategory.LIFECYCLE,
                calculator, calculatorRun, null, data("name", "计算员"));
        add(events, conversationId, turnId, "AGENT_START", SquadEventCategory.LIFECYCLE,
                writer, writerRun, null, data("name", "汇总员"));

        // 事件按真实发生顺序交错发射，前端必须依靠 agent.parentAgentInstanceId 建立归属。
        add(events, conversationId, turnId, "TOOL_CALL_START", SquadEventCategory.TOOL,
                researcher, researcherRun, relation("call-search", null),
                data("toolCallId", "call-search", "toolCallName", "资料检索"));
        add(events, conversationId, turnId, "TOOL_CALL_START", SquadEventCategory.TOOL,
                calculator, calculatorRun, relation("call-calculate", null),
                data("toolCallId", "call-calculate", "toolCallName", "数字计算"));
        add(events, conversationId, turnId, "TEXT_BLOCK_DELTA", SquadEventCategory.MESSAGE,
                writer, writerRun, null, data("blockId", "writer-text", "delta", "我正在准备汇总结构..."));
        add(events, conversationId, turnId, "TOOL_RESULT_TEXT_DELTA", SquadEventCategory.TOOL,
                researcher, researcherRun, relation("call-search", null),
                data("toolCallId", "call-search", "delta", "已找到智能体事件处理机制。"));
        add(events, conversationId, turnId, "TOOL_RESULT_TEXT_DELTA", SquadEventCategory.TOOL,
                calculator, calculatorRun, relation("call-calculate", null),
                data("toolCallId", "call-calculate", "delta", "结果：42"));
        add(events, conversationId, turnId, "AGENT_END", SquadEventCategory.LIFECYCLE,
                calculator, calculatorRun, null, data("result", "42"));
        add(events, conversationId, turnId, "AGENT_END", SquadEventCategory.LIFECYCLE,
                researcher, researcherRun, null, data("result", "已完成资料检索"));
        add(events, conversationId, turnId, "AGENT_END", SquadEventCategory.LIFECYCLE,
                writer, writerRun, null, data("result", "已完成汇总草稿"));

        add(events, conversationId, turnId, "TEXT_BLOCK_DELTA", SquadEventCategory.MESSAGE,
                master, masterRun, null, data("blockId", "master-answer", "delta", "三个子任务均已完成，汇总如下..."));
        add(events, conversationId, turnId, "AGENT_RESULT", SquadEventCategory.MESSAGE,
                master, masterRun, null, data("result", Map.of("content", List.of(
                        Map.of("type", "text", "text", "测试完成：三位协作成员均已执行并返回结果。")))));
        add(events, conversationId, turnId, "AGENT_END", SquadEventCategory.LIFECYCLE,
                master, masterRun, null, data("name", "主协调员"));
        return events;
    }

    private void add(List<SquadMsg> events, String conversationId, String turnId,
                     String eventType, SquadEventCategory category, SquadAgentRef agent,
                     SquadExecutionRef execution, SquadEventRelation relation, Map<String, Object> payload) {
        SquadMsg message = new SquadMsg();
        message.setProtocolVersion("1");
        message.setEventId(UUID.randomUUID().toString());
        message.setSequence(events.size() + 1L);
        message.setEventType(eventType);
        message.setCategory(category);
        message.setConversationId(conversationId);
        message.setTurnId(turnId);
        message.setAgent(agent);
        message.setNativeExecution(execution);
        message.setNativeSource(agent.getAgentType() == SquadAgentType.MASTER
                ? null : "master/" + agent.getAgentId());
        message.setRelation(relation);
        message.setPayload(payload);
        message.setTimestamp(Instant.now());
        events.add(message);
    }

    private SquadAgentRef agent(String id, String instanceId, String name, SquadAgentType type,
                                String parentInstanceId, int depth) {
        return new SquadAgentRef(id, instanceId, name, type, parentInstanceId,
                "master-instance", depth);
    }

    private SquadExecutionRef execution(String agentId, String sessionId, String turnId, String runId) {
        return new SquadExecutionRef(agentId, sessionId, turnId, runId);
    }

    private SquadEventRelation relation(String toolCallId, String targetAgentInstanceId) {
        return new SquadEventRelation(null, null, null, null, toolCallId, null, targetAgentInstanceId);
    }

    private Map<String, Object> data(Object... values) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (int i = 0; i + 1 < values.length; i += 2) {
            result.put(String.valueOf(values[i]), values[i + 1]);
        }
        return result;
    }

    @PreDestroy
    public void shutdown() {
        executor.shutdownNow();
    }
}
