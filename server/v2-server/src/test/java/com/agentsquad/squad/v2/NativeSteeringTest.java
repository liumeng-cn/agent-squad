package com.agentsquad.squad.v2;

import com.agentsquad.squad.v2.dto.ChatInterruptDTO;
import com.agentsquad.squad.v2.enums.ChatDisposition;
import com.agentsquad.squad.v2.enums.ChatInputStatus;
import com.agentsquad.squad.v2.service.ChatEventStreamService;
import com.agentsquad.squad.v2.service.ChatRuntimeService;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.message.ToolUseBlock;
import io.agentscope.core.model.ChatResponse;
import io.agentscope.core.model.Model;
import io.agentscope.harness.agent.HarnessAgent;
import io.agentscope.harness.agent.session.AgentSession;
import io.agentscope.harness.agent.subagent.SubagentDeclaration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * 原生运行中插话与观察隔离验证
 * <p>
 * 用门控模型固定竞争窗口，验证已接受插话会在收尾前应用，取消订阅不会取消执行。
 */
class NativeSteeringTest {
    @TempDir
    Path workspace;

    @Test
    void steeringSurvivesModelCompletionAndSubscriberDisconnect() throws Exception {
        Model model = mock(Model.class);
        when(model.getModelName()).thenReturn("gated-chat");
        var entered = new CountDownLatch(1);
        var gate = Sinks.<ChatResponse>one();
        var calls = new AtomicInteger();
        List<List<Msg>> observed = new CopyOnWriteArrayList<>();
        when(model.stream(any(), any(), any())).thenAnswer(invocation -> {
            observed.add(List.copyOf(invocation.getArgument(0)));
            if (calls.getAndIncrement() == 0) {
                entered.countDown();
                return gate.asMono().flux();
            }
            return Flux.just(text("已采用补充要求"));
        });
        try (var agent = HarnessAgent.builder().agentId("steering-test").name("test")
                .model(model).disableMemoryHooks().workspace(workspace).build()) {
            var sessions = AguiSessionTest.runtime(agent);
            var service = AguiSessionTest.service(sessions);
            var receipt = service.send(AguiSessionTest.input("initial", "整理报告"));
            assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
            var stream = new ChatEventStreamService();
            ReflectionTestUtils.setField(stream, "sessionService", sessions);
            var directory = (com.agentsquad.squad.v2.service.ChatConvService)
                    ReflectionTestUtils.getField(sessions, "convs");
            var authorized = directory.require("conversation");
            org.mockito.Mockito.doReturn(authorized).when(directory).requireOwned("conversation", 1L);
            org.mockito.Mockito.clearInvocations(directory);

            // 入口校验一次，后续快照复用句柄，不再查询目录或读取请求身份。
            var subscription = stream.subscribe("conversation");
            org.mockito.Mockito.when(directory.require("conversation"))
                    .thenThrow(new IllegalStateException("后台线程不能读取请求身份"));
            assertThat(subscription.next().block(Duration.ofSeconds(5))).isNotNull();
            org.mockito.Mockito.verify(directory, org.mockito.Mockito.never())
                    .requireOwned(anyString(), org.mockito.ArgumentMatchers.anyLong());
            org.mockito.Mockito.verify(directory, org.mockito.Mockito.times(1)).require("conversation");
            org.mockito.Mockito.doReturn(authorized).when(directory).require("conversation");

            var supplement = AguiSessionTest.input("supplement", "只统计华东地区");
            supplement.setObservedTurnId(receipt.getTurnId());
            var accepted = service.send(supplement);
            assertThat(accepted.getDisposition()).isEqualTo(ChatDisposition.STEERING);
            assertThat(accepted.getTurnId()).isEqualTo(receipt.getTurnId());
            assertThat(sessions.read("conversation").getInputs().getFirst().getStatus())
                    .isEqualTo(ChatInputStatus.ACCEPTED);
            gate.tryEmitValue(text("旧条件生成的初稿"));
            var nativeSession = sessions.open("conversation");
            assertThat(nativeSession.await(nativeSession.task(receipt.getInputId()))
                    .block(Duration.ofSeconds(20)).status()).isEqualTo("completed");
            assertThat(calls).hasValue(2);
            assertThat(observed.getLast()).anyMatch(message -> message.getTextContent().contains("只统计华东地区"));
            var result = sessions.read("conversation");
            assertThat(result.getInputs().getFirst().getStatus()).isEqualTo(ChatInputStatus.APPLIED);
            assertThat(result.getMessages().getLast().getContent()).isEqualTo("已采用补充要求");
            assertThat(result.getMessages()).extracting(message -> message.getContent())
                    .containsSubsequence("整理报告", "旧条件生成的初稿", "只统计华东地区", "已采用补充要求");
            assertThat(result.getMessages().stream().map(message -> message.getId()).distinct().count())
                    .isEqualTo(result.getMessages().size());
        }
    }

    @Test
    void interruptRequestsStopWithoutStartingAnotherExecution() throws Exception {
        Model model = mock(Model.class);
        when(model.getModelName()).thenReturn("interrupt-test");
        var entered = new CountDownLatch(1);
        var finish = Sinks.<ChatResponse>one();
        when(model.stream(any(), any(), any())).thenAnswer(invocation -> {
            entered.countDown();
            return finish.asMono().flux();
        });
        try (var agent = HarnessAgent.builder().agentId("interrupt-test").name("test")
                .model(model).disableMemoryHooks().workspace(workspace).build()) {
            var sessions = AguiSessionTest.runtime(agent);
            var service = AguiSessionTest.service(sessions);
            var receipt = service.send(AguiSessionTest.input("initial", "执行任务"));
            assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
            var request = new ChatInterruptDTO();
            request.setConvId("conversation");
            request.setRunId(sessions.open("conversation").log().head().owner());
            assertThat(service.interrupt(request)).isTrue();
            // 等分发器实际处理停止命令，再释放模型，固定原生协作中断边界。
            var inbox = new io.agentscope.core.session.SessionInbox(sessions.open("conversation").log());
            Flux.interval(Duration.ofMillis(10))
                    .filter(tick -> {
                        var view = inbox.snapshot();
                        return view.commands().stream().anyMatch(command -> command.kind().equals("interrupt")
                                && view.handled().contains(command.id()));
                    }).next().block(Duration.ofSeconds(5));
            finish.tryEmitValue(text("中断边界返回"));
            var session = sessions.open("conversation");
            assertThat(session.await(session.task(receipt.getInputId())).block(Duration.ofSeconds(20))).isNotNull();
            // 原生中断可能需要模型到达协作边界；收据不能被当成已停止的断言。
            assertThat(sessions.open("conversation").tasks()).hasSize(1);
            assertThat(io.agentscope.core.session.SessionTurns.read(session.log()).latest().status())
                    .isEqualTo("interrupted");
            // 重试旧请求不能取消可继续的轮次。
            service.send(AguiSessionTest.input("initial", "执行任务"));
            assertThat(io.agentscope.core.session.SessionTurns.read(session.log()).latest().status())
                    .isEqualTo("interrupted");
            when(model.stream(any(), any(), any())).thenReturn(Flux.just(text("新问题的回答")));
            var next = service.send(AguiSessionTest.input("next", "你好"));
            assertThat(next.getTurnId()).isNotEqualTo(receipt.getTurnId());
            assertThat(session.await(session.task(next.getInputId())).block(Duration.ofSeconds(20)).status())
                    .isEqualTo("completed");
            assertThat(io.agentscope.core.session.SessionTurns.read(session.log()).turns().getFirst().status())
                    .isEqualTo("cancelled");
            assertThat(sessions.read("conversation").getMessages())
                    .anyMatch(message -> message.getContent().equals("执行任务"))
                    .anyMatch(message -> message.getContent().equals("新问题的回答"));
            assertThat(receipt.getDisposition()).isEqualTo(ChatDisposition.NEW_TURN);
        }
    }

    @Test
    void masterSteeringWaitsForChildAndDoesNotRewriteChildInput() throws Exception {
        var model = mock(Model.class);
        when(model.getModelName()).thenReturn("child-gate-model");
        var childEntered = new CountDownLatch(1);
        var childGate = Sinks.<ChatResponse>one();
        var firstMaster = new AtomicBoolean(true);
        List<List<Msg>> childInputs = new CopyOnWriteArrayList<>();
        List<List<Msg>> parentInputs = new CopyOnWriteArrayList<>();
        when(model.stream(any(), any(), any())).thenAnswer(invocation -> {
            List<Msg> messages = invocation.getArgument(0);
            if (messages.stream().map(Msg::getTextContent).filter(java.util.Objects::nonNull)
                    .anyMatch(value -> value.contains("child-only-marker"))) {
                childInputs.add(List.copyOf(messages));
                childEntered.countDown();
                return childGate.asMono().flux();
            }
            parentInputs.add(List.copyOf(messages));
            if (firstMaster.getAndSet(false)) {
                return Flux.just(ChatResponse.builder().content(List.of(ToolUseBlock.builder()
                        .id("child-spawn").name("agent_spawn")
                        .content(io.agentscope.core.util.JsonUtils.getJsonCodec().toJson(
                                Map.of("agent_id", "gated-child", "task", "执行子任务")))
                        .build())).finishReason("tool_calls").build());
            }
            return Flux.just(text("主任务完成"));
        });
        try (var agent = HarnessAgent.builder().agentId("parent-test").name("parent")
                .model(model).disableMemoryHooks().disableDynamicSubagents().workspace(workspace)
                .subagent(SubagentDeclaration.builder().name("gated-child").description("受控子助手")
                        .inlineAgentsBody("child-only-marker，你是受控子助手").steps(4).build()).build()) {
            var sessions = AguiSessionTest.runtime(agent);
            var service = AguiSessionTest.service(sessions);
            var initial = service.send(AguiSessionTest.input("parent", "启动子任务"));
            boolean enteredChild = childEntered.await(10, TimeUnit.SECONDS);
            assertThat(enteredChild).withFailMessage("child entry absent: parentCalls=%s, state=%s, toolResults=%s",
                    parentInputs.size(), sessions.read("conversation").getStatus(),
                    parentInputs.stream().flatMap(java.util.Collection::stream)
                            .flatMap(message -> message.getContentBlocks(io.agentscope.core.message.ToolResultBlock.class).stream())
                            .flatMap(result -> result.getOutput().stream()).filter(TextBlock.class::isInstance)
                            .map(TextBlock.class::cast).map(TextBlock::getText).toList()).isTrue();
            var extra = AguiSessionTest.input("parent-extra", "主任务只统计本月");
            extra.setObservedTurnId(initial.getTurnId());
            assertThat(service.send(extra).getDisposition()).isEqualTo(ChatDisposition.STEERING);
            childGate.tryEmitValue(text("子任务完成"));
            var session = sessions.open("conversation");
            assertThat(session.await(session.task(initial.getInputId())).block(Duration.ofSeconds(20)).status())
                    .isEqualTo("completed");
            assertThat(childInputs).allSatisfy(messages -> assertThat(messages)
                    .noneMatch(message -> message.getTextContent().contains("主任务只统计本月")));
            assertThat(parentInputs.getLast()).anyMatch(message -> message.getTextContent().contains("主任务只统计本月"));
            assertThat(sessions.read("conversation").getInputs().getFirst().getStatus())
                    .isEqualTo(ChatInputStatus.APPLIED);
        }
    }

    @Test
    void uncertainSteeringFailureNeverFallsBackToNewSubmission() {
        var sessions = mock(ChatRuntimeService.class);
        var session = mock(AgentSession.class);
        when(sessions.open("conversation")).thenReturn(session);
        when(session.steer(any(AgentSession.Task.class), anyString()))
                .thenThrow(new IllegalStateException("storage result unknown"));
        var request = AguiSessionTest.input("extra", "补充要求");
        request.setObservedTurnId("observed-turn");
        assertThatThrownBy(() -> AguiSessionTest.service(sessions).send(request))
                .isInstanceOf(IllegalStateException.class);
        verify(session, never()).submit(anyString(), anyString());
    }

    private ChatResponse text(String value) {
        return ChatResponse.builder().content(List.of(TextBlock.builder().text(value).build()))
                .finishReason("stop").build();
    }
}
