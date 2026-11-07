package com.agentsquad.squad.runtime;

import com.agentsquad.squad.agent.tool.RequestAcceptanceReviewTool;
import com.agentsquad.squad.domain.entity.UserAcceptanceReview;
import com.agentsquad.squad.domain.dto.AcceptanceReviewRequest;
import com.agentsquad.squad.runtime.service.PlanAcceptanceService;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.message.ToolUseBlock;
import io.agentscope.core.tool.ToolCallParam;
import io.agentscope.core.tool.ToolSuspendException;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 用户验收工具挂起验证
 * <p>
 * 确认工具使用可信身份核对条件后进入 SDK 挂起流程，不自行生成通过答复。
 */
class RequestAcceptanceReviewToolTest {
    @Test
    void suspendsForActualUserAnswer() {
        var service = mock(PlanAcceptanceService.class);
        var tool = new RequestAcceptanceReviewTool();
        ReflectionTestUtils.setField(tool, "acceptance", service);
        ReflectionTestUtils.setField(tool, "json", new ObjectMapper());
        var param = mock(ToolCallParam.class);
        when(param.getRuntimeContext()).thenReturn(RuntimeContext.builder().userId("user").sessionId("session").build());
        when(param.getToolUseBlock()).thenReturn(mock(ToolUseBlock.class));
        when(param.getInput()).thenReturn(Map.of("criterionId", "confirm", "expectedPlanVersion", 2));
        var request = new AcceptanceReviewRequest("confirm", 2);
        when(service.prepareUserReview("user", "session", request)).thenReturn(new UserAcceptanceReview("confirm", 2, "请确认结果", "hash"));
        assertThatThrownBy(() -> tool.callAsync(param).block(Duration.ofSeconds(3))).isInstanceOf(ToolSuspendException.class);
        verify(service).prepareUserReview("user", "session", request);
    }
}
