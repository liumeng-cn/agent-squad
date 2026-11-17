package com.agentsquad.squad.service;

import com.agentsquad.squad.exceptions.RuntimeBusinessException;
import com.agentsquad.squad.enums.RuntimeExceptionCode;
import com.agentsquad.squad.utils.ContentDigests;
import com.agentsquad.squad.model.bo.AcceptedModelChoiceBO;
import com.agentsquad.squad.enums.ReasoningAdapter;
import com.agentsquad.squad.enums.ReasoningMode;
import com.agentsquad.squad.model.vo.ModelOptionVO;
import com.agentsquad.squad.config.properties.WorkerModelProfileProperties;
import com.agentsquad.squad.config.properties.WorkerProfileProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.agentscope.core.model.GenerateOptions;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 普通回复与Worker共用的模型目录
 * <p>
 * 配置集中在既有模型Profile；受理固定配置身份，运行前校验不被配置修改隐式换模，密钥轮换不改变指纹。
 */
@Service
public class ModelCatalogService {
    @Resource
    private WorkerProfileProperties registry;

    @Resource
    private ObjectMapper json;

    public List<ModelOptionVO> list() {
        return registry.getModelProfiles().entrySet().stream()
                .filter(entry -> entry.getValue().isEnabled() && present(entry.getValue().getApiKey())
                        && present(entry.getValue().getModelName()) && present(entry.getValue().getBaseUrl()))
                .sorted(Comparator.comparing(entry -> entry.getKey()))
                .map(entry -> {
                    var profile = registry.requirePricedModel(entry.getKey());
                    return new ModelOptionVO(entry.getKey(), present(profile.getDisplayName()) ? profile.getDisplayName() : profile.getModelName(),
                            profile.getModelName(), List.copyOf(profile.getCapabilities()), List.copyOf(profile.getReasoningModes()), profile.getDefaultReasoningMode());
                }).toList();
    }

    /**
     * 只校验受理配置，不在提交事务中调用供应商；未知模型或不支持的模式直接拒绝。
     */
    public AcceptedModelChoiceBO accept(String requestedId, ReasoningMode requestedMode) {
        String id = requestedId == null ? "default" : requestedId;
        if (!id.matches("[A-Za-z0-9][A-Za-z0-9._-]{0,99}") || !registry.getModelProfiles().containsKey(id)) {
            throw invalid("所选模型不存在，请重新选择");
        }
        var profile = registry.requirePricedModel(id);
        if (!profile.isEnabled()) {
            throw invalid("所选模型当前不可用，请选择其他模型");
        }
        ReasoningMode mode = requestedMode == null ? profile.getDefaultReasoningMode() : requestedMode;
        if (!profile.getReasoningModes().contains(mode) || !supported(profile.getReasoningAdapter(), mode)) {
            throw invalid("所选模型不支持该推理模式");
        }
        return new AcceptedModelChoiceBO(id, version(profile), mode);
    }

    /**
     * 页面选择和配置变化都不能重写在途模型；配置已变化时明确失败，不回退另一个模型。
     */
    public WorkerModelProfileProperties resolve(AcceptedModelChoiceBO choice) {
        choice = canonicalize(choice);
        var profile = registry.requireExecutableModel(choice.modelId());
        if (!profile.isEnabled() || !version(profile).equals(choice.configurationVersion())) {
            throw new RuntimeBusinessException(RuntimeExceptionCode.CONFLICT, "该回答的模型配置已变化，请重新发起");
        }
        return profile;
    }

    /**
     * 默认模型切换后，旧回复仅能匹配配置指纹完全相同的显式配置，不静默换模。
     */
    public AcceptedModelChoiceBO canonicalize(AcceptedModelChoiceBO choice) {
        if (!"default".equals(choice.modelId())
                || version(registry.requirePricedModel("default")).equals(choice.configurationVersion())) {
            return choice;
        }
        return registry.getModelProfiles().entrySet().stream()
                .filter(entry -> !"default".equals(entry.getKey()) && entry.getValue().isEnabled()
                        && version(entry.getValue()).equals(choice.configurationVersion()))
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> new AcceptedModelChoiceBO(entry.getKey(), choice.configurationVersion(), choice.reasoningMode()))
                .findFirst().orElse(choice);
    }

    /**
     * 将目录声明的思考模式翻译成供应商参数；聊天、协调与 Worker 共用，避免模型与参数协议错配。
     */
    public GenerateOptions.Builder applyReasoning(
            GenerateOptions.Builder options, WorkerModelProfileProperties profile, ReasoningMode mode) {
        if (!supported(profile.getReasoningAdapter(), mode)) {
            throw invalid("模型不支持该思考模式");
        }
        if (mode == ReasoningMode.DEFAULT) {
            return options;
        }
        switch (profile.getReasoningAdapter()) {
            case QWEN_THINKING -> options.additionalBodyParam("enable_thinking", mode == ReasoningMode.ON);
            case SPARK_THINKING -> options.additionalBodyParam("thinking", Map.of("type", switch (mode) {
                case OFF -> "disabled";
                case AUTO -> "auto";
                case ON -> "enabled";
                default -> throw invalid("模型不支持该思考模式");
            }));
            case OPENAI_EFFORT -> options.reasoningEffort(mode.name().toLowerCase(Locale.ROOT));
            case NONE -> throw invalid("该模型未声明思考参数协议");
        }
        return options;
    }

    public String encode(AcceptedModelChoiceBO choice) {
        try {
            return json.writeValueAsString(choice);
        } catch (JsonProcessingException failure) {
            throw new IllegalStateException("模型选择无法持久化", failure);
        }
    }

    public AcceptedModelChoiceBO decode(String value) {
        try {
            return json.readValue(value, AcceptedModelChoiceBO.class);
        } catch (JsonProcessingException failure) {
            throw new IllegalStateException("已受理模型选择无法读取", failure);
        }
    }

    private String version(WorkerModelProfileProperties profile) {
        try {
            return ContentDigests.sha256(json.writeValueAsString(Arrays.asList(profile.getProvider(), profile.getModelName(),
                    profile.getBaseUrl(), profile.getPricingVersion(), profile.getInputMicrounitsPerMillionTokens(),
                    profile.getOutputMicrounitsPerMillionTokens(), profile.getMaxInputTokens(), profile.getMaxOutputTokens(),
                    profile.getReasoningAdapter())));
        } catch (JsonProcessingException failure) {
            throw new IllegalStateException("模型配置无法生成版本", failure);
        }
    }

    private boolean supported(ReasoningAdapter adapter, ReasoningMode mode) {
        if (mode == ReasoningMode.DEFAULT) return true;
        return switch (adapter) {
            case NONE -> false;
            case QWEN_THINKING -> Set.of(ReasoningMode.OFF, ReasoningMode.ON).contains(mode);
            case SPARK_THINKING -> Set.of(ReasoningMode.OFF, ReasoningMode.AUTO, ReasoningMode.ON).contains(mode);
            case OPENAI_EFFORT -> Set.of(ReasoningMode.LOW, ReasoningMode.MEDIUM, ReasoningMode.HIGH).contains(mode);
        };
    }

    private boolean present(String value) {
        return value != null && !value.isBlank();
    }

    private RuntimeBusinessException invalid(String message) {
        return new RuntimeBusinessException(RuntimeExceptionCode.VALIDATION, message);
    }
}
