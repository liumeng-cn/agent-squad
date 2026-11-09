package com.agentsquad.squad.worker.profile;

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

    private java.util.List<com.agentsquad.squad.domain.enums.ModelCapability> capabilities = java.util.List.of();

    private com.agentsquad.squad.domain.enums.ReasoningAdapter reasoningAdapter = com.agentsquad.squad.domain.enums.ReasoningAdapter.NONE;

    private java.util.List<com.agentsquad.squad.domain.enums.ReasoningMode> reasoningModes = java.util.List.of(com.agentsquad.squad.domain.enums.ReasoningMode.DEFAULT);

    private com.agentsquad.squad.domain.enums.ReasoningMode defaultReasoningMode = com.agentsquad.squad.domain.enums.ReasoningMode.DEFAULT;

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
