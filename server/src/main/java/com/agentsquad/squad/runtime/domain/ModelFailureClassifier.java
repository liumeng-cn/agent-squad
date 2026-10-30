package com.agentsquad.squad.runtime.domain;

import com.agentsquad.squad.common.exception.BudgetExceededException;
import io.agentscope.core.model.ModelHttpException;

import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.util.concurrent.TimeoutException;

/**
 * 模型调用故障的纯分类规则。
 * <p>
 * 供应商鉴权属于系统配置责任，不能把平台模型凭据错误交给普通用户修理。
 */
public final class ModelFailureClassifier {
    private ModelFailureClassifier() {
    }

    public static FailureClassification classify(Throwable failure) {
        Throwable current = failure;
        for (int depth = 0; current != null && depth < 12; depth++, current = current.getCause()) {
            if (current instanceof ModelHttpException http && http.getStatusCode() != null) {
                int status = http.getStatusCode();
                if (status == 429 || status == 408 || status >= 500 && status <= 599) {
                    return new FailureClassification("SERVICE_TRANSIENT", "MODEL_TEMPORARILY_UNAVAILABLE", "SYSTEM", "回答服务暂时不可用，正在尝试恢复。");
                }
                if (status == 401 || status == 403) {
                    return new FailureClassification("AUTH_REQUIRED", "MODEL_CREDENTIALS_INVALID", "SYSTEM", "回答服务的授权暂时不可用，已保留你的内容。这个问题需要由系统处理。");
                }
                if (status == 404) {
                    return new FailureClassification("ENVIRONMENT", "MODEL_UNAVAILABLE", "SYSTEM", "当前所需的回答能力暂不可用，已保留你的内容。");
                }
                return new FailureClassification("LOGIC_INVALID", "MODEL_REQUEST_REJECTED", "SYSTEM", "本次处理请求未能通过校验，已保留你的内容。");
            }
            if (current instanceof ConnectException || current instanceof SocketTimeoutException || current instanceof TimeoutException) {
                return new FailureClassification("SERVICE_TRANSIENT", "MODEL_CONNECTION_INTERRUPTED", "SYSTEM", "回答服务连接暂时中断，正在尝试恢复。");
            }
            if (current instanceof PlanCorrectionExhaustedException) {
                return new FailureClassification("LOGIC_INVALID", "PLAN_CORRECTION_EXHAUSTED", "SYSTEM", "处理方案未能通过校验，已停止继续尝试并保留你的内容。");
            }
            if (current instanceof com.agentsquad.squad.skill.domain.SkillCapabilityUnavailableException) {
                return new FailureClassification("ENVIRONMENT", "SKILL_CAPABILITY_UNAVAILABLE", "SYSTEM",
                        "本次所需技能依赖的工具暂不可用，已保留你的内容；系统不会自动安装或扩大授权。");
            }
            if (current instanceof ExecutionContextInvalidatedException) {
                return new FailureClassification("AUTH_REQUIRED", "EXECUTION_CONTEXT_INVALIDATED", "SYSTEM",
                        "技能授权或记忆已变化，已停止使用旧上下文，请重新发起。");
            }
            if (current instanceof BudgetExceededException) {
                return new FailureClassification("ENVIRONMENT", "BUDGET_EXHAUSTED", "SYSTEM", "本次可用处理额度已用完，已保留你的消息和已完成内容。");
            }
        }
        return new FailureClassification("INTERNAL", "EXECUTION_INTERRUPTED", "SYSTEM", "本次处理未能完成，已保留你的内容，系统已记录问题。");
    }
}
