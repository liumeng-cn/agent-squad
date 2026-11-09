package com.agentsquad.squad.runtime;

import com.agentsquad.squad.exceptions.PendingDirectivesException;
import com.agentsquad.squad.exceptions.RuntimeBusinessException;
import com.agentsquad.squad.model.po.TaskDirectivePO;
import com.agentsquad.squad.model.bo.TaskGoalStateBO;
import com.agentsquad.squad.model.po.TaskPO;
import com.agentsquad.squad.mapper.TaskDirectiveMapper;
import com.agentsquad.squad.mapper.TaskMapper;
import com.agentsquad.squad.service.ExecutionScopeService;
import com.agentsquad.squad.model.bo.PlanContextBO;
import com.agentsquad.squad.model.bo.PlanNodeBO;
import com.agentsquad.squad.model.bo.PlanPublishBO;
import com.agentsquad.squad.mapper.PlanMapper;
import com.agentsquad.squad.service.PlanService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 早到目标补充的确定性反馈测试。
 * <p>
 * 验证多条待处理修改使用真实标识和当前任务版本，反馈不包含用户正文。
 */
class PlanPendingDirectiveTest {
    @Test
    void shouldReturnAllOwnedDirectivesWithoutPrivateContent() throws Exception {
        PlanService service = new PlanService();
        TaskDirectiveMapper directives = mock(TaskDirectiveMapper.class);
        TaskMapper tasks = mock(TaskMapper.class);
        PlanMapper plans = mock(PlanMapper.class);
        ExecutionScopeService scopes = mock(ExecutionScopeService.class);
        ObjectMapper json = new ObjectMapper();
        ReflectionTestUtils.setField(service, "directives", directives);
        ReflectionTestUtils.setField(service, "tasks", tasks);
        ReflectionTestUtils.setField(service, "planMapper", plans);
        ReflectionTestUtils.setField(service, "executionScopes", scopes);
        ReflectionTestUtils.setField(service, "objectMapper", json);
        when(plans.lockMasterContext("user", "session")).thenReturn(
                new PlanContextBO("user", "task", "binding", 0, "RUNNING", false, 1, "ACTIVE"));
        when(directives.goalState("user", "task")).thenReturn(new TaskGoalStateBO(1, "{}"));
        when(directives.pending("user", "task")).thenReturn(List.of("first", "second"));
        TaskPO task = mock(TaskPO.class);
        when(task.getVersion()).thenReturn(9L);
        when(tasks.lockOwnedRow("user", "task")).thenReturn(task);
        for (String id : List.of("first", "second")) {
            TaskDirectivePO row = mock(TaskDirectivePO.class);
            when(row.getDirectiveId()).thenReturn(id);
            when(row.getTaskId()).thenReturn("task");
            when(row.getStatus()).thenReturn("ACCEPTED");
            when(row.getKind()).thenReturn("SUPPLEMENT");
            when(row.getContent()).thenReturn("private-directive-text");
            when(row.getExpectedVersion()).thenReturn(2L);
            when(directives.lock("user", id)).thenReturn(row);
        }
        PlanPublishBO request = new PlanPublishBO("plan", 0,
                List.of(new PlanNodeBO("node", "worker", "v1", "goal", "snapshot", List.of(), true, 0)),
                "ALL_REQUIRED", List.of("done"));
        RuntimeBusinessException failure = assertThrows(RuntimeBusinessException.class,
                () -> service.publish("user", "session", request));
        var pending = (PendingDirectivesException) failure;
        assertThat(pending.directives()).hasSize(2);
        assertThat(pending.directives().get(0).directiveId()).isEqualTo("first");
        assertThat(pending.directives().get(1).directiveId()).isEqualTo("second");
        assertThat(pending.directives().get(0).expectedVersion()).isEqualTo(9);
        assertThat(pending.directives().get(0).content()).isEqualTo("private-directive-text");
        assertThat(failure.getMessage()).doesNotContain("private-directive-text");
    }
}
