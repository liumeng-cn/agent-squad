package com.agentsquad.squad.v2.service;

import com.agentsquad.squad.v2.dto.ChatInterruptDTO;
import com.agentsquad.squad.v2.dto.ChatMessageDTO;
import com.agentsquad.squad.v2.dto.ChatResumeDTO;
import com.agentsquad.squad.v2.enums.ChatDisposition;
import com.agentsquad.squad.v2.enums.NativeChatStatus;
import com.agentsquad.squad.v2.enums.NativeSteeringRejection;
import com.agentsquad.squad.v2.vo.ChatReceiptVO;
import io.agentscope.core.session.SessionRecovery;
import io.agentscope.core.session.SessionTurns;
import io.agentscope.harness.agent.session.AgentSession;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 持续对话服务
 * <p>
 * 通过原生持久命令执行、补充和停止；执行由 SDK 持有，与页面订阅生命周期分离。
 */
@Slf4j
@Service
public class ChatService {
    @Resource
    private ChatSessionService sessionService;

    /**
     * 优先向页面指定的运行轮次插话；停止后发送新问题时，通过原生恢复接口结束旧轮次。
     * 网络或存储失败不转为提交，防止不确定结果导致重复执行。
     */
    public ChatReceiptVO send(ChatMessageDTO request, String userId) {
        var session = sessionService.open(request.getSessionId(), userId);
        if (request.getObservedTurnId() != null) {
            // 固定 SDK 的 steer(Task, String) 只使用 turnId；不先查当前任务替代客户端的明确目标。
            var target = new AgentSession.Task(null, request.getObservedTurnId(), null, null, null);
            try {
                var input = session.steer(target, request.getMessage().trim());
                return receipt(request, input.turnId(), input.inputId(), ChatDisposition.STEERING);
            } catch (IllegalStateException failure) {
                // 只有明确的准入竞争拒绝且目标轮次已完成，才允许开启下一轮。
                if (NativeSteeringRejection.fromMessage(failure.getMessage()) == NativeSteeringRejection.UNKNOWN
                        || !ended(session, request.getObservedTurnId())) {
                    throw failure;
                }
            }
        }
        synchronized (session) {
            // 已接收的请求重试交给 SDK 校验，不取消重试期间产生的其他轮次。
            boolean accepted = session.tasks().stream()
                    .anyMatch(task -> task.commandId().equals(request.getInteractionId()));
            var latest = SessionTurns.read(session.log()).latest();
            if (!accepted && latest != null) {
                var status = NativeChatStatus.fromValue(latest.status());
                if (status == NativeChatStatus.INTERRUPTED || status == NativeChatStatus.FAILED) {
                    // SDK 获取写租约并拒绝结果未知的工具；保留旧轮次历史，显式结束后再提交。
                    SessionRecovery.cancelPending(session.log(), latest.turnId());
                }
            }
            var task = session.submit(request.getInteractionId(), request.getMessage().trim());
            return receipt(request, task.turnId(), task.commandId(), ChatDisposition.NEW_TURN);
        }
    }

    /**
     * 停止请求交给原生 Inbox，返回值只表示接收，不代表工具已停止。
     */
    public boolean interrupt(ChatInterruptDTO request, String userId) {
        boolean accepted = sessionService.open(request.getSessionId(), userId).interrupt(request.getRunId());
        log.info("聊天停止请求，sessionId={}，runId={}，accepted={}",
                request.getSessionId(), request.getRunId(), accepted);
        return accepted;
    }

    /**
     * 显式继续原轮次；不自动恢复结果未知的工具调用。
     */
    public String resume(ChatResumeDTO request, String userId) {
        var session = sessionService.open(request.getSessionId(), userId);
        synchronized (session) {
            return session.resume(request.getTurnId()).commandId();
        }
    }

    private boolean ended(AgentSession session, String turnId) {
        return SessionTurns.read(session.log()).turns().stream()
                .anyMatch(turn -> turn.turnId().equals(turnId) && turn.ended());
    }

    private ChatReceiptVO receipt(ChatMessageDTO request, String turnId, String inputId, ChatDisposition disposition) {
        var receipt = new ChatReceiptVO();
        receipt.setSessionId(request.getSessionId());
        receipt.setInteractionId(request.getInteractionId());
        receipt.setTurnId(turnId);
        receipt.setInputId(inputId);
        receipt.setDisposition(disposition);
        log.info("聊天输入已接收，sessionId={}，interactionId={}，turnId={}，disposition={}",
                request.getSessionId(), request.getInteractionId(), turnId, disposition);
        return receipt;
    }
}
