package com.agentsquad.squad.runtime.service;

import com.agentsquad.squad.presentation.service.TaskEventStreamer;
import com.agentsquad.squad.common.exception.RuntimeBusinessException;
import com.agentsquad.squad.conversation.domain.TaskRow;
import com.agentsquad.squad.conversation.repository.TaskEventMapper;
import com.agentsquad.squad.conversation.repository.TaskMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 任务事件连接容量测试。
 * <p>
 * 验证共享轮询器在连接满额时拒绝新订阅，避免页面连接无界占用服务资源。
 */
class TaskEventStreamerTest {

    private TaskEventStreamer streamer;

    @AfterEach
    void tearDown() {
        if (streamer != null) {
            streamer.shutdown();
        }
    }

    @Test
    void rejectsConnectionsBeyondConfiguredLimit() {
        TaskMapper taskMapper = mock(TaskMapper.class);
        TaskEventMapper eventMapper = mock(TaskEventMapper.class);
        when(taskMapper.findOwned(anyString(), anyString())).thenReturn(mock(TaskRow.class));
        when(eventMapper.listAfter(anyString(), anyString(), anyLong())).thenReturn(List.of());

        streamer = new TaskEventStreamer();
        ReflectionTestUtils.setField(streamer, "taskMapper", taskMapper);
        ReflectionTestUtils.setField(streamer, "taskEventMapper", eventMapper);
        ReflectionTestUtils.setField(streamer, "pollThreads", 1);
        ReflectionTestUtils.setField(streamer, "maxConnections", 2);
        streamer.initialize();

        streamer.stream("user-1", "task-1", 0);
        streamer.stream("user-1", "task-2", 0);

        assertThatThrownBy(() -> streamer.stream("user-1", "task-3", 0))
                .isInstanceOf(RuntimeBusinessException.class)
                .hasMessageContaining("事件连接已满");
    }
}
