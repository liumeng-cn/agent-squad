package com.agentsquad.squad.pending;

import com.agentsquad.squad.enums.ModelCapability;
import com.agentsquad.squad.enums.ReasoningMode;

import java.util.List;

/**
 * 用户可选择的模型公开信息
 * <p>
 * 模型目录只公开名称、能力和支持的思考模式，不公开供应商地址、密钥或内部定价配置。
 */
public record ModelOption(String modelId, String displayName, String modelName,
                          List<ModelCapability> capabilities, List<ReasoningMode> reasoningModes,
                          ReasoningMode defaultReasoningMode) {
}
