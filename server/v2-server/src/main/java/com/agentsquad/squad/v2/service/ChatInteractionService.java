package com.agentsquad.squad.v2.service;

import com.agentsquad.squad.common.enums.ApiCode;
import com.agentsquad.squad.common.exceptions.BusinessException;
import com.agentsquad.squad.v2.agent.tool.AskUserTool;
import com.agentsquad.squad.v2.dto.ChatAnswerDTO;
import com.agentsquad.squad.v2.enums.NativeChatCommandType;
import com.agentsquad.squad.v2.enums.NativeChatInteractionKind;
import com.agentsquad.squad.v2.enums.NativeChatStateRejection;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.message.ToolResultBlock;
import io.agentscope.core.message.ToolUseBlock;
import io.agentscope.core.session.SessionInbox;
import io.agentscope.core.util.JsonUtils;
import io.agentscope.harness.agent.session.AgentSession;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 用户交互答复处理
 * <p>
 * 聊天模块先核对用户归属，再校验原生待答事项并继续原轮次。
 * 幂等依据原生 Inbox 中的答复命令，不另建状态表，不允许伪造其他工具结果。
 */
@Slf4j
@Service
public class ChatInteractionService {
    @Resource
    private ChatRuntimeService runtime;

    /**
     * 提交问题答案
     * <p>
     * 同一待答事项的相同答案重试返回原命令；不同答案拒绝覆盖。
     * SDK 受理和执行分离，网络失败后可重试，不重新提交用户任务。
     */
    public String answer(ChatAnswerDTO request) {
        AgentSession session = runtime.open(request.getConvId());
        String answer = request.getAnswer().trim();
        synchronized (session) {
            String accepted = acceptedAnswer(session, request, answer);
            if (accepted != null) {
                return accepted;
            }

            var pending = session.pending().get(request.getRequestId());
            if (pending == null || !request.getTurnId().equals(pending.turnId())) {
                throw new BusinessException(ApiCode.CONFLICT, "该问题已处理或不属于当前轮次，请刷新会话");
            }
            var data = pending.data();
            ToolUseBlock call = JsonUtils.getJsonCodec().convertValue(data.get("call"), ToolUseBlock.class);
            if (data.containsKey("protocol")
                    || NativeChatInteractionKind.fromValue(data.get("kind")) != NativeChatInteractionKind.EXTERNAL_EXECUTION
                    || call == null || !request.getRequestId().equals(call.getId())
                    || !AskUserTool.NAME.equals(call.getName())) {
                throw new BusinessException(ApiCode.BAD_REQUEST, "该待答事项不支持通过问题答案处理");
            }

            try {
                var task = session.respond(request.getRequestId(), answer);
                log.info("用户答案已受理，convId={}，turnId={}，requestId={}，commandId={}",
                        request.getConvId(), request.getTurnId(), request.getRequestId(), task.commandId());
                return task.commandId();
            } catch (IllegalStateException exception) {
                var rejection = NativeChatStateRejection.fromMessage(exception.getMessage());
                if (rejection != NativeChatStateRejection.UNKNOWN) {
                    throw new BusinessException(ApiCode.CONFLICT, rejection.getDesc());
                }
                throw exception;
            }
        }
    }

    private String acceptedAnswer(AgentSession session, ChatAnswerDTO request, String answer) {
        var inbox = new SessionInbox(session.log()).snapshot();
        for (var command : inbox.commands()) {
            if (NativeChatCommandType.fromValue(command.kind()) != NativeChatCommandType.RESPOND
                    || !command.requestIds().equals(List.of(request.getRequestId()))) {
                continue;
            }
            if (!request.getTurnId().equals(command.turnId()) || !answer.equals(answerText(command))) {
                throw new BusinessException(ApiCode.CONFLICT, "该问题已提交其他答案，请刷新会话");
            }
            if (inbox.rejected().containsKey(command.id())) {
                throw new BusinessException(ApiCode.CONFLICT, "该答案未能应用，请刷新会话核对执行状态");
            }
            return command.id();
        }
        return null;
    }

    private String answerText(SessionInbox.Command command) {
        for (var message : command.messages()) {
            for (var result : message.getContentBlocks(ToolResultBlock.class)) {
                if (!AskUserTool.NAME.equals(result.getName())) {
                    continue;
                }
                if (result.getOutput().size() == 1 && result.getOutput().getFirst() instanceof TextBlock text) {
                    return text.getText();
                }
            }
        }
        return null;
    }
}
