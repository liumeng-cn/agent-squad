package com.agentsquad.squad.config.properties;

import com.agentsquad.squad.enums.ModelCapability;
import com.agentsquad.squad.enums.ReasoningAdapter;
import com.agentsquad.squad.enums.ReasoningMode;
import lombok.Getter;
import lombok.Setter;

/**
 * Worker 模型能力配置。
 * <p>
 * 由服务端配置绑定，包含定价与请求额度；密钥只在运行时使用，不写入 Definition 或业务事件。
 */
@Getter
@Setter
public class WorkerModelProfileProperties {

    /** 是否启用当前配置项 */
    private boolean enabled = true;

    /** 供用户识别的模型显示名称 */
    private String displayName;

    /** 模型允许声明的能力集合 */
    private java.util.List<ModelCapability> capabilities = java.util.List.of();

    /** 请求推理参数时使用的模型适配类型 */
    private ReasoningAdapter reasoningAdapter = ReasoningAdapter.NONE;

    /** 模型允许选择的推理模式 */
    private java.util.List<ReasoningMode> reasoningModes = java.util.List.of(ReasoningMode.DEFAULT);

    /** 未指定时使用的推理模式 */
    private ReasoningMode defaultReasoningMode = ReasoningMode.DEFAULT;

    /** 模型提供方标识 */
    private String provider;

    /** 发送到模型提供方的模型标识 */
    private String modelName;

    /** 模型服务的基础地址 */
    private String baseUrl;

    /** 访问模型服务所需的凭据 */
    private String apiKey;

    /** 预算计价规则的版本标识 */
    private String pricingVersion;

    /** 每百万输入 token 的价格，单位微计价单位 */
    private long inputMicrounitsPerMillionTokens;

    /** 每百万输出 token 的价格，单位微计价单位 */
    private long outputMicrounitsPerMillionTokens;

    /** 单次模型请求的输入 token 上限 */
    private int maxInputTokens;

    /** 单次模型请求的输出 token 上限 */
    private int maxOutputTokens;
}
