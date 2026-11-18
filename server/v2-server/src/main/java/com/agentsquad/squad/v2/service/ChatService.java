package com.agentsquad.squad.v2.service;

import com.agentsquad.squad.common.enums.ApiCode;
import com.agentsquad.squad.common.exceptions.BusinessException;

import com.agentsquad.squad.v2.dto.ChatInterruptDTO;
import com.agentsquad.squad.v2.dto.ChatMessageDTO;
import com.agentsquad.squad.v2.dto.ChatResumeDTO;
import com.agentsquad.squad.v2.enums.ChatDisposition;
import com.agentsquad.squad.v2.enums.NativeChatStatus;
import com.agentsquad.squad.v2.enums.SteeringRejection;
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
    private ChatRuntimeService sessionService;

    /**
     * 优先向页面指定的运行轮次插话；停止后发送新问题时，通过原生恢复接口结束旧轮次。
     * 网络或存储失败不转为提交，防止不确定结果导致重复执行。
     */
    public ChatReceiptVO send(ChatMessageDTO request, String userId) {

        if (sessionService.isPreview(request.getSessionId())) {
            throw new BusinessException(ApiCode.BAD_REQUEST, "示例会话仅供展示，请开启新对话");
        }

        var session = sessionService.open(request.getSessionId(), userId);
        if (request.getObservedTurnId() != null) {

            // 插话
            var target = new AgentSession.Task(null, request.getObservedTurnId(), null, null, null);
            try {
                var input = session.steer(target, request.getMessage().trim());
                return receipt(request, input.turnId(), input.inputId(), ChatDisposition.STEERING);

            } catch (IllegalStateException e) {

                log.warn("插话失败，sessionId:{}, msg:{}", request.getSessionId(), e.getMessage());

                var rejection = SteeringRejection.fromMessage(e.getMessage());
                if (rejection == SteeringRejection.UNKNOWN || !ended(session, request.getObservedTurnId())) {
                    throw e;
                }
            }
        }

        synchronized (session) {
            boolean accepted = hasAcceptedTask(session, request.getInteractionId());
            var latest = SessionTurns.read(session.log()).latest();
            if (!accepted && latest != null) {
                var status = NativeChatStatus.fromValue(latest.status());
                if (status == NativeChatStatus.INTERRUPTED || status == NativeChatStatus.FAILED) {
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

        boolean accepted = sessionService.open(request.getSessionId(), userId)
                .interrupt(request.getRunId());
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

    /**
     * 判断本次发送是否已被接收，避免重试时取消其他轮次
     */
    private boolean hasAcceptedTask(AgentSession session, String interactionId) {
        for (var task : session.tasks()) {
            if (task.commandId().equals(interactionId)) {
                return true;
            }
        }
        return false;
    }

    private boolean ended(AgentSession session, String turnId) {
        for (var turn : SessionTurns.read(session.log()).turns()) {
            if (turn.turnId().equals(turnId) && turn.ended()) {
                return true;
            }
        }
        return false;
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
