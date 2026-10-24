package com.agentsquad.squad.interaction.service;

import com.agentsquad.squad.identity.AuthView;
import com.agentsquad.squad.common.exception.RuntimeBusinessException;
import com.agentsquad.squad.interaction.domain.InteractionRow;
import com.agentsquad.squad.interaction.repository.InteractionMapper;
import com.agentsquad.squad.tools.operation.repository.OperationMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

/**
 * 审批答复前置校验测试。
 * <p>
 * 验证过期版本在进入外部操作账本前被拒绝，避免产生不可撤销的外部写入。
 */
class InteractionServiceTest {

    @Test
    void shouldRejectStaleApprovalBeforeAccessingOperation() {
        InteractionService service = new InteractionService();
        InteractionMapper interactionMapper = mock(InteractionMapper.class);
        OperationMapper operationMapper = mock(OperationMapper.class);
        ReflectionTestUtils.setField(service, "interactionMapper", interactionMapper);
        ReflectionTestUtils.setField(service, "operationMapper", operationMapper);
        ReflectionTestUtils.setField(service, "objectMapper", new ObjectMapper());

        AuthView actor = new AuthView("user-1", "tester", List.of("USER"), "csrf");
        InteractionRow row = new InteractionRow("interaction-1", actor.userId(), "task-1",
                "job-1", "binding-1", "operation-1", "FRAMEWORK", "request-1",
                "APPROVAL", "确认执行", "{}", 0L, "hash-1", 0L,
                LocalDateTime.now().plusHours(1), "OPEN", 2L, null);
        when(interactionMapper.findOwned(actor.userId(), row.interactionId())).thenReturn(row);

        assertThrows(RuntimeBusinessException.class,
                () -> service.respond(actor, row.interactionId(), "key-1", 1L,
                        "APPROVAL", true, row.requestHash(), null));
        verifyNoInteractions(operationMapper);
    }
}
