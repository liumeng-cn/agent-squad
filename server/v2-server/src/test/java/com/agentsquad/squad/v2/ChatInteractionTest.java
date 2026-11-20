package com.agentsquad.squad.v2;

import com.agentsquad.squad.common.exceptions.BusinessException;
import com.agentsquad.squad.v2.agent.tool.AskUserTool;
import com.agentsquad.squad.v2.dto.ChatAnswerDTO;
import com.agentsquad.squad.v2.enums.ChatExecutionStatus;
import com.agentsquad.squad.v2.service.ChatInteractionService;
import com.agentsquad.squad.v2.service.ChatRuntimeService;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.message.ToolResultBlock;
import io.agentscope.core.message.ToolUseBlock;
import io.agentscope.core.model.ChatResponse;
import io.agentscope.core.model.Model;
import io.agentscope.core.session.SessionEvent;
import io.agentscope.core.session.SessionLog;
import io.agentscope.core.tool.Toolkit;
import io.agentscope.core.tool.ToolSuspendException;
import io.agentscope.core.util.JsonUtils;
import io.agentscope.harness.agent.HarnessAgent;
import io.agentscope.harness.agent.session.AgentSession;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;
import reactor.core.publisher.Flux;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * 原生用户交互验证
 * <p>
 * 使用真实 Harness 和可控模型验证挂起、重开恢复、答案幂等与原轮次续跑。
 */
class ChatInteractionTest {
    @TempDir
    Path workspace;

    @Test
    void validatesQuestionBeforeSuspending() {
        var tool = new AskUserTool();
        assertThatThrownBy(() -> tool.ask(" ", null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> tool.ask("选哪个？", List.of(""))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> tool.ask("选哪个？", List.of("方案一", "方案二")))
                .isInstanceOf(ToolSuspendException.class);
    }

    @Test
    void refusesToInventResultsForOtherExternalTools() {
        var runtime = mock(ChatRuntimeService.class);
        var session = mock(AgentSession.class);
        var log = mock(SessionLog.class);
        var inbox = mock(SessionLog.class);
        when(runtime.open("conversation")).thenReturn(session);
        when(session.log()).thenReturn(log);
        when(log.inbox()).thenReturn(inbox);
        when(inbox.head()).thenReturn(new SessionLog.Head(0, 0, null, 0, null));
        var call = Map.of("type", "tool_use", "id", "external-call", "name", "create_order", "input", Map.of());
        var event = new SessionEvent(1, "request-event", 1, 1000, "interaction/requested", "run", "turn", true,
                JsonUtils.getJsonCodec().toJson(Map.of("requestId", "external-call", "kind", "external_execution", "call", call)));
        when(session.pending()).thenReturn(Map.of("external-call", event));
        var service = new ChatInteractionService();
        ReflectionTestUtils.setField(service, "runtime", runtime);
        assertThatThrownBy(() -> service.answer(answer("turn", "external-call", "成功")))
                .isInstanceOf(BusinessException.class);
        verify(session, never()).respond(any(String.class), any(String.class));
    }

    @Test
    void restoresPendingQuestionAndResumesSameTurnWithoutDuplicateAnswer() {
        var calls = new AtomicInteger();
        Model model = model(calls);
        String turnId;
        try (var agent = agent(model)) {
            var runtime = AguiSessionTest.runtime(agent);
            var session = runtime.open("conversation");
            var task = session.submit("question-input", "请先让我选择方案");
            assertThat(session.await(task).block(Duration.ofSeconds(20)).status()).isEqualTo("suspended");
            turnId = task.turnId();
            var snapshot = runtime.read("conversation");
            assertThat(snapshot.getStatus()).isEqualTo(ChatExecutionStatus.WAITING);
            assertThat(snapshot.getInteractions()).singleElement().satisfies(question -> {
                assertThat(question.getRequestId()).isEqualTo("question-1");
                assertThat(question.getQuestion()).isEqualTo("要采用哪个方案？");
                assertThat(question.getOptions()).containsExactly("方案一", "取消");
            });
        }

        try (var agent = agent(model)) {
            var runtime = AguiSessionTest.runtime(agent);
            assertThat(runtime.read("conversation").getInteractions()).hasSize(1);
            assertThat(AguiSessionTest.runtime(agent, "2").read("conversation").getInteractions()).isEmpty();
            var service = new ChatInteractionService();
            ReflectionTestUtils.setField(service, "runtime", runtime);
            var wrongTurn = answer("wrong-turn", "question-1", "取消");
            assertThatThrownBy(() -> service.answer(wrongTurn)).isInstanceOf(BusinessException.class);
            assertThatThrownBy(() -> service.answer(answer(turnId, "invented", "取消")))
                    .isInstanceOf(BusinessException.class);

            var request = answer(turnId, "question-1", "取消");
            String commandId = service.answer(request);
            assertThat(service.answer(request)).isEqualTo(commandId);
            var session = runtime.open("conversation");
            var completed = session.await(session.task(commandId)).block(Duration.ofSeconds(20));
            assertThat(completed.status()).isEqualTo("completed");
            assertThat(completed.turnId()).isEqualTo(turnId);
            assertThat(service.answer(request)).isEqualTo(commandId);
            assertThatThrownBy(() -> service.answer(answer(turnId, "question-1", "方案一")))
                    .isInstanceOf(BusinessException.class);

            var snapshot = runtime.read("conversation");
            assertThat(snapshot.getInteractions()).isEmpty();
            assertThat(snapshot.getAnsweredRequestIds()).containsExactly("question-1");
            assertThat(snapshot.getStatus()).isEqualTo(ChatExecutionStatus.COMPLETED);
            assertThat(snapshot.getMessages()).anySatisfy(message ->
                    assertThat(message.getContent()).isEqualTo("问题：要采用哪个方案？\n回答：取消"));
            assertThat(snapshot.getMessages().getLast().getContent()).isEqualTo("已收到你的回答");
            assertThat(calls).hasValue(2);
        }
    }

    @Test
    void answersMultipleQuestionsSeparatelyWithoutDuplicatingPendingCards() {
        var calls = new AtomicInteger();
        var model = mock(Model.class);
        when(model.getModelName()).thenReturn("multiple-question-model");
        when(model.stream(any(), any(), any())).thenAnswer(invocation -> {
            if (calls.getAndIncrement() == 0) {
                var first = ToolUseBlock.builder().id("question-first").name(AskUserTool.NAME)
                        .content("{\"question\":\"选择地区？\"}").build();
                var second = ToolUseBlock.builder().id("question-second").name(AskUserTool.NAME)
                        .content("{\"question\":\"选择月份？\"}").build();
                return Flux.just(ChatResponse.builder().content(List.of(first, second))
                        .finishReason("tool_calls").build());
            }
            return Flux.just(ChatResponse.builder().content(List.of(TextBlock.builder()
                    .text("信息已收齐").build())).finishReason("stop").build());
        });
        try (var agent = agent(model)) {
            var runtime = AguiSessionTest.runtime(agent);
            var session = runtime.open("conversation");
            var initial = session.submit("multiple-questions", "请补充查询条件");
            assertThat(session.await(initial).block(Duration.ofSeconds(20)).status()).isEqualTo("suspended");
            assertThat(runtime.read("conversation").getInteractions()).hasSize(2);
            var service = new ChatInteractionService();
            ReflectionTestUtils.setField(service, "runtime", runtime);
            String first = service.answer(answer(initial.turnId(), "question-first", "华东"));
            assertThat(session.await(session.task(first)).block(Duration.ofSeconds(20)).status()).isEqualTo("suspended");
            assertThat(runtime.read("conversation").getInteractions()).singleElement()
                    .satisfies(question -> assertThat(question.getRequestId()).isEqualTo("question-second"));
            String second = service.answer(answer(initial.turnId(), "question-second", "本月"));
            assertThat(session.await(session.task(second)).block(Duration.ofSeconds(20)).status()).isEqualTo("completed");
            assertThat(runtime.read("conversation").getInteractions()).isEmpty();
            assertThat(calls).hasValue(2);
        }
    }

    private HarnessAgent agent(Model model) {
        var toolkit = new Toolkit();
        toolkit.registerTool(new AskUserTool());
        return HarnessAgent.builder().agentId("question-test").name("test")
                .model(model).toolkit(toolkit).workspace(workspace).disableMemoryHooks()
                .disableFilesystemTools().disableShellTool().disableWebTools().disableSubagents().build();
    }

    private Model model(AtomicInteger calls) {
        var model = mock(Model.class);
        when(model.getModelName()).thenReturn("question-model");
        when(model.stream(any(), any(), any())).thenAnswer(invocation -> {
            if (calls.getAndIncrement() == 0) {
                var call = ToolUseBlock.builder().id("question-1").name(AskUserTool.NAME)
                        .content(JsonUtils.getJsonCodec().toJson(Map.of(
                                "question", "要采用哪个方案？", "options", List.of("方案一", "取消")))).build();
                return Flux.just(ChatResponse.builder().content(List.of(call)).finishReason("tool_calls").build());
            }
            List<io.agentscope.core.message.Msg> messages = invocation.getArgument(0);
            assertThat(messages).anySatisfy(message ->
                    assertThat(message.getContentBlocks(ToolResultBlock.class)).anySatisfy(result -> {
                        assertThat(result.getName()).isEqualTo(AskUserTool.NAME);
                        assertThat(result.getOutput()).singleElement().satisfies(block ->
                                assertThat(((TextBlock) block).getText()).isEqualTo("取消"));
                    }));
            return Flux.just(ChatResponse.builder().content(List.of(TextBlock.builder()
                    .text("已收到你的回答").build())).finishReason("stop").build());
        });
        return model;
    }

    private ChatAnswerDTO answer(String turnId, String requestId, String text) {
        var request = new ChatAnswerDTO();
        request.setConvId("conversation");
        request.setTurnId(turnId);
        request.setRequestId(requestId);
        request.setAnswer(text);
        return request;
    }
}
