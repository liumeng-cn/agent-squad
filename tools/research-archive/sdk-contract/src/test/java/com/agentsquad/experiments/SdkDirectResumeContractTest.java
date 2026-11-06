package com.agentsquad.experiments;

import io.agentscope.core.tool.Toolkit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 终结工具审批恢复与补充输入准入实验
 * <p>
 * 使用真实Session提交、respond和steer，核对工具效果、模型次数及已提交输出，不模拟SDK调度算法。
 */
class SdkDirectResumeContractTest {
    @Test
    void externallyResolvedBatchAcrossTwoResumesReturnsToModel(@TempDir Path directory) throws Exception {
        var finish = new ControlledFinishTool(ControlledFinishMode.EXTERNAL);
        var lookup = new ControlledFinishTool("lookup", ControlledFinishMode.EXTERNAL);
        var toolkit = new Toolkit();
        toolkit.registerAgentTool(finish);
        toolkit.registerAgentTool(lookup);
        try (var model = new ScriptedModelServer(List.of(ScriptedModelServer.tools(true), ScriptedModelServer.text("stop")), false);
             var agent = SdkExecutionContractTest.agent(model, directory, toolkit, 4);
             var session = SdkExecutionContractTest.session(agent)) {
            assertThat(session.await(session.submit("split", "分批恢复实验")).block(Duration.ofSeconds(15)).status())
                    .isEqualTo("suspended");
            assertThat(session.pending()).hasSize(2);
            var first = session.respond(session.pending().keySet().iterator().next(), "第一份外部结果");
            assertThat(session.await(first).block(Duration.ofSeconds(15)).status()).isEqualTo("suspended");
            assertThat(session.pending()).hasSize(1);
            assertThat(model.calls()).isEqualTo(1);
            var second = session.respond(session.pending().keySet().iterator().next(), "第二份外部结果");
            assertThat(session.await(second).block(Duration.ofSeconds(15)).status()).isEqualTo("completed");
            assertThat(model.calls()).isEqualTo(2);
            assertThat(finish.calls()).isZero();
            assertThat(lookup.calls()).isZero();
            var outputs = session.log().readAfter(0, 1000).stream()
                    .filter(event -> event.type().equals("turn/output")).toList();
            assertThat(outputs).hasSize(1);
            assertThat(outputs.getFirst().payloadJson()).doesNotContain("TOOL_RETURN_DIRECT");
        }
    }

    @Test
    void newSubmissionDuringDirectToolRemainsAnIndependentTurn(@TempDir Path directory) throws Exception {
        var tool = new ControlledFinishTool(ControlledFinishMode.BLOCKED);
        var toolkit = new Toolkit();
        toolkit.registerAgentTool(tool);
        try (var model = new ScriptedModelServer(List.of(ScriptedModelServer.tools(false), ScriptedModelServer.text("stop")), false);
             var agent = SdkExecutionContractTest.agent(model, directory, toolkit, 4);
             var session = SdkExecutionContractTest.session(agent)) {
            var first = session.submit("first", "第一轮实验");
            assertThat(tool.awaitEntered()).isTrue();
            var second = session.submit("second", "第二轮实验");
            assertThat(second.turnId()).isNotEqualTo(first.turnId());
            tool.release();
            assertThat(session.await(first).block(Duration.ofSeconds(15)).status()).isEqualTo("completed");
            assertThat(session.await(second).block(Duration.ofSeconds(15)).status()).isEqualTo("completed");
            assertThat(model.calls()).isEqualTo(2);
            assertThat(tool.calls()).isEqualTo(1);
            var outputs = session.log().readAfter(0, 1000).stream()
                    .filter(event -> event.type().equals("turn/output")).toList();
            assertThat(outputs).hasSize(2);
            assertThat(outputs.stream().map(event -> event.turnId()).distinct()).hasSize(2);
        } finally {
            tool.release();
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void approvalControlsExecutionAndDirectReturn(boolean approved, @TempDir Path directory) throws Exception {
        var tool = new ControlledFinishTool(ControlledFinishMode.APPROVAL);
        var toolkit = new Toolkit();
        toolkit.registerAgentTool(tool);
        try (var model = new ScriptedModelServer(List.of(ScriptedModelServer.tools(false), ScriptedModelServer.text("stop")), false);
             var agent = SdkExecutionContractTest.agent(model, directory, toolkit, 4);
             var session = SdkExecutionContractTest.session(agent)) {
            var initial = session.await(session.submit("approval", "审批实验")).block(Duration.ofSeconds(15));
            assertThat(initial.status()).isEqualTo("suspended");
            assertThat(tool.calls()).isZero();
            assertThat(session.pending()).hasSize(1);
            String request = session.pending().keySet().iterator().next();
            var answer = session.respond(request, approved);
            var settled = session.await(answer).block(Duration.ofSeconds(15));
            assertThat(settled.status()).isEqualTo("completed");
            assertThat(tool.calls()).isEqualTo(approved ? 1 : 0);
            assertThat(model.calls()).isEqualTo(approved ? 1 : 2);
            assertThat(session.pending()).isEmpty();
            var outputs = session.log().readAfter(0, 1000).stream()
                    .filter(event -> event.type().equals("turn/output")).toList();
            assertThat(outputs).hasSize(1);
            if (approved) {
                assertThat(outputs.getFirst().payloadJson()).contains("TOOL_RETURN_DIRECT", "终结工具实验结果");
            } else {
                assertThat(outputs.getFirst().payloadJson()).doesNotContain("TOOL_RETURN_DIRECT", "终结工具实验结果");
            }
        }
    }

    @Test
    void externalResultResumesWithoutAnotherModelCall(@TempDir Path directory) throws Exception {
        var tool = new ControlledFinishTool(ControlledFinishMode.SUSPEND);
        var toolkit = new Toolkit();
        toolkit.registerAgentTool(tool);
        try (var model = new ScriptedModelServer(List.of(ScriptedModelServer.tools(false)), false);
             var agent = SdkExecutionContractTest.agent(model, directory, toolkit, 4);
             var session = SdkExecutionContractTest.session(agent)) {
            var initial = session.await(session.submit("external", "外部执行实验")).block(Duration.ofSeconds(15));
            assertThat(initial.status()).isEqualTo("suspended");
            assertThat(session.pending()).hasSize(1);
            var answer = session.respond(session.pending().keySet().iterator().next(), "外部结果已核实");
            assertThat(session.await(answer).block(Duration.ofSeconds(15)).status()).isEqualTo("completed");
            assertThat(model.calls()).isEqualTo(1);
            assertThat(tool.calls()).isEqualTo(1);
            assertThat(session.pending()).isEmpty();
            var outputs = session.log().readAfter(0, 1000).stream()
                    .filter(event -> event.type().equals("turn/output")).toList();
            assertThat(outputs).hasSize(1);
            assertThat(outputs.getFirst().payloadJson()).contains("TOOL_RETURN_DIRECT", "外部结果已核实");
        }
    }

    @Test
    void steeringAcceptedDuringDirectToolIsAppliedBeforeTurnCloses(@TempDir Path directory) throws Exception {
        var tool = new ControlledFinishTool(ControlledFinishMode.BLOCKED);
        var toolkit = new Toolkit();
        toolkit.registerAgentTool(tool);
        try (var model = new ScriptedModelServer(List.of(ScriptedModelServer.tools(false), ScriptedModelServer.text("stop")), false);
             var agent = SdkExecutionContractTest.agent(model, directory, toolkit, 4);
             var session = SdkExecutionContractTest.session(agent)) {
            var receipt = session.submit("steering", "补充输入实验");
            assertThat(tool.awaitEntered()).isTrue();
            var input = session.steer(receipt, "实验补充要求");
            tool.release();
            assertThat(session.await(receipt).block(Duration.ofSeconds(15)).status()).isEqualTo("completed");
            assertThat(tool.calls()).isEqualTo(1);
            assertThat(model.calls()).isEqualTo(2);
            var events = session.log().readAfter(0, 1000);
            assertThat(events).anyMatch(event -> event.type().equals("inbox/applied")
                    && event.payloadJson().contains(input.inputId()));
            assertThat(events.stream().filter(event -> event.type().equals("turn/output")).toList()).hasSize(1);
        } finally {
            tool.release();
        }
    }
}
