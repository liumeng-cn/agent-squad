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
        when(plans.lockMasterContext(1L, "session")).thenReturn(
                new PlanContextBO(1L, 2L, 3L, 0, "RUNNING", false, 1, "ACTIVE"));
        when(directives.goalState(1L, 2L)).thenReturn(new TaskGoalStateBO(1, "{}"));
        when(directives.pending(1L, 2L)).thenReturn(List.of(11L, 12L));
        TaskPO task = mock(TaskPO.class);
        when(task.getVersion()).thenReturn(9L);
        when(tasks.lockOwnedRow(1L, 2L)).thenReturn(task);
        for (Long id : List.of(11L, 12L)) {
            TaskDirectivePO row = mock(TaskDirectivePO.class);
            when(row.getDirectiveId()).thenReturn(id);
            when(row.getTaskId()).thenReturn(2L);
            when(row.getStatus()).thenReturn("ACCEPTED");
            when(row.getKind()).thenReturn("SUPPLEMENT");
            when(row.getContent()).thenReturn("private-directive-text");
            when(row.getExpectedVersion()).thenReturn(2L);
            when(directives.lock(1L, id)).thenReturn(row);
        }
        PlanPublishBO request = new PlanPublishBO("plan", 0,
                List.of(new PlanNodeBO("node", "worker", "v1", "goal", 4L, List.of(), true, 0)),
                "ALL_REQUIRED", List.of("done"));
        RuntimeBusinessException failure = assertThrows(RuntimeBusinessException.class,
                () -> service.publish(1L, "session", request));
        var pending = (PendingDirectivesException) failure;
        assertThat(pending.directives()).hasSize(2);
        assertThat(pending.directives().get(0).directiveId()).isEqualTo(11L);
        assertThat(pending.directives().get(1).directiveId()).isEqualTo(12L);
        assertThat(pending.directives().get(0).expectedVersion()).isEqualTo(9);
        assertThat(pending.directives().get(0).content()).isEqualTo("private-directive-text");
        assertThat(failure.getMessage()).doesNotContain("private-directive-text");
    }
}
