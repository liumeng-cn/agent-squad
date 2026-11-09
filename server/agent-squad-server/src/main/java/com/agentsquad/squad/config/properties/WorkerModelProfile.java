package com.agentsquad.squad.config.properties;

import com.agentsquad.squad.enums.ModelCapability;
import com.agentsquad.squad.enums.ReasoningAdapter;
import com.agentsquad.squad.enums.ReasoningMode;
import lombok.Getter;
import lombok.Setter;

/**
 * Worker 模型能力配置。
 *
 * 由服务端配置绑定，包含定价与请求额度；密钥只在运行时使用，不写入 Definition 或业务事件。
 */
@Getter
@Setter
public class WorkerModelProfile {

    private boolean enabled = true;

    private String displayName;

    private java.util.List<ModelCapability> capabilities = java.util.List.of();

    private ReasoningAdapter reasoningAdapter = ReasoningAdapter.NONE;

    private java.util.List<ReasoningMode> reasoningModes = java.util.List.of(ReasoningMode.DEFAULT);

    private ReasoningMode defaultReasoningMode = ReasoningMode.DEFAULT;

    private String provider;

    private String modelName;

    private String baseUrl;

    private String apiKey;

    private String pricingVersion;

    private long inputMicrounitsPerMillionTokens;

    private long outputMicrounitsPerMillionTokens;

    private int maxInputTokens;

    private int maxOutputTokens;
}
