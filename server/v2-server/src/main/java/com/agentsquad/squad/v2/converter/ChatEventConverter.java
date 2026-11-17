package com.agentsquad.squad.v2.converter;

import com.agentsquad.squad.v2.enums.ChatEventType;
import com.agentsquad.squad.v2.vo.ChatEventVO;
import io.agentscope.core.agui.event.AguiEvent;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * 聊天事件协议转换
 * <p>
 * 按明确类型提取公开字段；未支持的 SDK 事件不进入业务输出，避免框架扩展隐式改变接口。
 */
@Component
public class ChatEventConverter {

    /**
     * 转换页面支持的事件，保留身份和时间；内部输入、原始事件及工具结果正文不参与映射。
     */
    public Optional<ChatEventVO> convert(AguiEvent source) {
        var target = new ChatEventVO();
        target.setThreadId(source.getThreadId());
        target.setRunId(source.getRunId());
        target.setTimestamp(source.timestamp());

        switch (source) {
            case AguiEvent.RunStarted ignored -> target.setType(ChatEventType.RUN_STARTED);
            case AguiEvent.RunFinished event -> {
                target.setType(ChatEventType.RUN_FINISHED);
                target.setWaiting(event.outcome() instanceof AguiEvent.RunFinishedInterruptOutcome);
            }
            case AguiEvent.RunError ignored -> {
                target.setType(ChatEventType.RUN_ERROR);
                target.setMessage("执行失败，请稍后重试");
            }
            case AguiEvent.TextMessageContent event -> {
                target.setType(ChatEventType.TEXT_MESSAGE_CONTENT);
                target.setMessageId(event.messageId());
                target.setDelta(event.delta());
            }
            case AguiEvent.ReasoningMessageContent event -> {
                target.setType(ChatEventType.REASONING_MESSAGE_CONTENT);
                target.setMessageId(event.messageId());
                target.setDelta(event.delta());
            }
            case AguiEvent.ToolCallStart event -> {
                target.setType(ChatEventType.TOOL_CALL_START);
                target.setToolCallId(event.toolCallId());
                target.setToolCallName(event.toolCallName());
            }
            case AguiEvent.ToolCallResult event -> {
                target.setType(ChatEventType.TOOL_CALL_RESULT);
                target.setToolCallId(event.toolCallId());
                target.setMessageId(event.messageId());
            }
            default -> {
                return Optional.empty();
            }
        }

        return Optional.of(target);
    }
}
