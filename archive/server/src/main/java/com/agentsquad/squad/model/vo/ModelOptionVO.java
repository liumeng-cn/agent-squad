package com.agentsquad.squad.model.vo;

import com.agentsquad.squad.enums.ModelCapability;
import com.agentsquad.squad.enums.ReasoningMode;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

/**
 * 用户可选择的模型公开信息
 *
 * <p>仅提供模型展示信息、能力和支持的推理模式。</p>
 */
@Getter
@AllArgsConstructor
public class ModelOptionVO {

    private final String modelId;

    private final String displayName;

    private final String modelName;

    private final List<ModelCapability> capabilities;

    private final List<ReasoningMode> reasoningModes;

    private final ReasoningMode defaultReasoningMode;
}
