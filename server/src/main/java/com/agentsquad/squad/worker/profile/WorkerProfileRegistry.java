package com.agentsquad.squad.worker.profile;

import com.agentsquad.squad.common.exception.RuntimeBusinessException;
import com.agentsquad.squad.common.exception.RuntimeExceptionCode;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * Worker 模型与运行 Profile 注册表。
 *
 * 已发布 Definition 只保存引用键；发布与启动均由该服务端配置解析，未知键不得回退默认值。
 */
@Component
@ConfigurationProperties(prefix = "agentsquad.worker")
@Getter
@Setter
public class WorkerProfileRegistry {

    private Map<String, WorkerModelProfile> modelProfiles = new HashMap<>();

    private Map<String, WorkerRuntimeProfile> runtimeProfiles = new HashMap<>();

    /**
     * 发布阶段检查引用是否已注册，不要求离线环境已填入真实模型密钥。
     */
    public void requireRegistered(String modelKey, String runtimeKey) {
        if (!modelProfiles.containsKey(modelKey) || !runtimeProfiles.containsKey(runtimeKey)) {
            throw new RuntimeBusinessException(RuntimeExceptionCode.VALIDATION,
                    "Worker Profile 未注册");
        }
    }

    /**
     * 启动时再次解析模型配置，防止配置移除后默默使用其他模型。
     */
    public WorkerModelProfile requireExecutableModel(String key) {
        WorkerModelProfile profile = requirePricedModel(key);
        if (isBlank(profile.getModelName()) || isBlank(profile.getBaseUrl())
                || isBlank(profile.getApiKey())) {
            throw new RuntimeBusinessException(RuntimeExceptionCode.CONFLICT,
                    "Worker 模型 Profile 不可执行");
        }
        return profile;
    }

    /**
     * 计划发布只校验定价和最大用量；真实密钥由执行时验证。
     */
    public WorkerModelProfile requirePricedModel(String key) {
        WorkerModelProfile profile = modelProfiles.get(key);
        if (profile == null || !"OPENAI_COMPATIBLE".equals(profile.getProvider())
                || isBlank(profile.getPricingVersion())
                || profile.getMaxInputTokens() <= 0
                || profile.getMaxOutputTokens() <= 0
                || profile.getInputMicrounitsPerMillionTokens() < 0
                || profile.getOutputMicrounitsPerMillionTokens() < 0) {
            throw new RuntimeBusinessException(RuntimeExceptionCode.CONFLICT,
                    "Worker 模型 Profile 定价或额度无效");
        }
        return profile;
    }

    /**
     * 启动时读取运行上限；定义里引用的配置缺失或非法时拒绝创建实例。
     */
    public WorkerRuntimeProfile requireRuntime(String key) {
        WorkerRuntimeProfile profile = runtimeProfiles.get(key);
        if (profile == null || profile.getMaxModelCalls() <= 0 || profile.getMaxToolCalls() < 0
                || profile.getMaxRecoveryAttempts() < 0 || profile.getMaxRecoveryAttempts() > 3
                || profile.getModelRequestTimeoutSeconds() <= 0
                || profile.getToolTimeoutSeconds() <= 0) {
            throw new RuntimeBusinessException(RuntimeExceptionCode.CONFLICT,
                    "Worker 运行 Profile 不可用");
        }
        return profile;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
