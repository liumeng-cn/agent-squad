package com.agentsquad.squad.converter;

import com.agentsquad.squad.model.bo.FailureClassificationBO;

import com.agentsquad.squad.exceptions.BudgetExceededException;
import com.agentsquad.squad.exceptions.ExecutionContextInvalidatedException;
import com.agentsquad.squad.exceptions.PlanCorrectionExhaustedException;
import com.agentsquad.squad.exceptions.SkillCapabilityUnavailableException;
import io.agentscope.core.model.ModelHttpException;
import io.agentscope.core.model.transport.HttpTransportException;
import io.agentscope.extensions.model.openai.exception.OpenAIException;

import java.io.IOException;

import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.util.concurrent.TimeoutException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 模型调用故障的纯分类规则。
 * <p>
 * 供应商鉴权属于系统配置责任，不能把平台模型凭据错误交给普通用户修理。
 * 只读取供应商错误码做归类，不把错误正文带进分类结果。
 */
public final class ModelFailureClassifier {
    private static final Pattern BODY_CODE = Pattern.compile("\"code\"\\s*:\\s*\"([^\"]{1,64})\"");
    private static final int VENDOR_CODE_MAX_CHARS = 64;

    private ModelFailureClassifier() {
    }

    public static FailureClassificationBO classify(Throwable failure) {
        Throwable current = failure;
        boolean modelTransport = false;
        for (int depth = 0; current != null && depth < 12; depth++, current = current.getCause()) {
            if (current instanceof HttpTransportException) {
                modelTransport = true;
            }
            if (current instanceof ModelHttpException http && http.getStatusCode() != null) {
                return classifyHttpStatus(http.getStatusCode(), vendorErrorCode(current));
            }
            // SDK 传输异常不实现 ModelHttpException，但携带状态码与响应正文，按同样规则归类。
            if (current instanceof HttpTransportException transport
                    && transport.getStatusCode() != null && transport.isHttpError()) {
                return classifyHttpStatus(transport.getStatusCode(), vendorErrorCode(current));
            }
            if (current instanceof ConnectException || current instanceof SocketTimeoutException || current instanceof TimeoutException) {
                return new FailureClassificationBO("SERVICE_TRANSIENT", "MODEL_CONNECTION_INTERRUPTED", "SYSTEM", "回答服务连接暂时中断，正在尝试恢复。");
            }
            // 仅模型HTTP传输链内的IO失败允许恢复，不能把本地文件故障当成模型断流。
            if (modelTransport && current instanceof IOException) {
                return new FailureClassificationBO("SERVICE_TRANSIENT", "MODEL_CONNECTION_INTERRUPTED", "SYSTEM",
                        "回答服务连接暂时中断，正在尝试恢复。");
            }
            if (current instanceof PlanCorrectionExhaustedException) {
                return new FailureClassificationBO("LOGIC_INVALID", "PLAN_CORRECTION_EXHAUSTED", "SYSTEM", "处理方案未能通过校验，已停止继续尝试并保留你的内容。");
            }
            if (current instanceof SkillCapabilityUnavailableException) {
                return new FailureClassificationBO("ENVIRONMENT", "SKILL_CAPABILITY_UNAVAILABLE", "SYSTEM",
                        "本次所需技能依赖的工具暂不可用，已保留你的内容；系统不会自动安装或扩大授权。");
            }
            if (current instanceof ExecutionContextInvalidatedException) {
                return new FailureClassificationBO("AUTH_REQUIRED", "EXECUTION_CONTEXT_INVALIDATED", "SYSTEM",
                        "技能授权或记忆已变化，已停止使用旧上下文，请重新发起。");
            }
            if (current instanceof BudgetExceededException) {
                return new FailureClassificationBO("ENVIRONMENT", "BUDGET_EXHAUSTED", "SYSTEM", "本次可用处理额度已用完，已保留你的消息和已完成内容。");
            }
        }
        return new FailureClassificationBO("INTERNAL", "EXECUTION_INTERRUPTED", "SYSTEM", "本次处理未能完成，已保留你的内容，系统已记录问题。");
    }

    /**
     * 按 HTTP 状态与供应商错误码归类；百炼等供应商把欠费和未开通放在 400 的错误码里，避免误报成请求校验失败。
     */
    private static FailureClassificationBO classifyHttpStatus(int status, String vendorCode) {
        if (status == 429 || status == 408 || status >= 500 && status <= 599) {
            return new FailureClassificationBO("SERVICE_TRANSIENT", "MODEL_TEMPORARILY_UNAVAILABLE", "SYSTEM", "回答服务暂时不可用，正在尝试恢复。");
        }
        if (status == 401 || status == 403) {
            return new FailureClassificationBO("AUTH_REQUIRED", "MODEL_CREDENTIALS_INVALID", "SYSTEM", "回答服务的授权暂时不可用，已保留你的内容。这个问题需要由系统处理。");
        }
        if (status == 402) {
            return new FailureClassificationBO("ENVIRONMENT", "MODEL_BILLING_UNAVAILABLE", "SYSTEM",
                    "回答服务暂时异常，未能完成这次回答。你的消息已保留。");
        }
        if (status == 404) {
            return new FailureClassificationBO("ENVIRONMENT", "MODEL_UNAVAILABLE", "SYSTEM", "当前所需的回答能力暂不可用，已保留你的内容。");
        }
        if (vendorCode != null) {
            if (vendorCode.contains("Arrearage")) {
                return new FailureClassificationBO("ENVIRONMENT", "MODEL_BILLING_UNAVAILABLE", "SYSTEM",
                        "回答服务暂时异常，未能完成这次回答。你的消息已保留。");
            }
            if (vendorCode.contains("Unpurchased") || vendorCode.contains("AccessDenied")) {
                return new FailureClassificationBO("ENVIRONMENT", "MODEL_ACCESS_DENIED", "SYSTEM",
                        "当前模型未开通或试用已失效，已保留你的内容。");
            }
        }
        return new FailureClassificationBO("LOGIC_INVALID", "MODEL_REQUEST_REJECTED", "SYSTEM", "本次处理请求未能通过校验，已保留你的内容。");
    }

    /**
     * 取供应商错误码（OpenAI 兼容扩展优先取结构化错误码；缺失时从响应正文解析），供欠费/未开通归类。
     */
    private static String vendorErrorCode(Throwable current) {
        if (current instanceof OpenAIException openAi) {
            String code = bounded(openAi.getErrorCode());
            // SDK 对 HTTP 错误只传状态码和正文，错误码缺失时正文才是唯一来源。
            return code != null ? code : codeFromBody(openAi.getResponseBody());
        }
        if (current instanceof HttpTransportException transport) {
            return codeFromBody(transport.getResponseBody());
        }
        return null;
    }

    private static String codeFromBody(String body) {
        if (body == null) {
            return null;
        }
        Matcher matcher = BODY_CODE.matcher(body);
        return matcher.find() ? bounded(matcher.group(1)) : null;
    }

    private static String bounded(String code) {
        if (code == null) {
            return null;
        }
        String trimmed = code.trim();
        return trimmed.isEmpty() || trimmed.length() > VENDOR_CODE_MAX_CHARS ? null : trimmed;
    }
}
