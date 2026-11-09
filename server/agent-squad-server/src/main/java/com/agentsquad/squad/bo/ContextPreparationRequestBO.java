package com.agentsquad.squad.bo;

import java.util.List;

/**
 * 局部材料选择请求。
 * <p>
 * 模型只能缩小已固定的授权集合；服务端验证版本归属，不接受模型提供资料正文或用户身份。
 */
public record ContextPreparationRequestBO(String targetNodeKey, String logicalKey,
                                        List<String> skillVersionIds, List<String> memoryIds) {
    public ContextPreparationRequestBO {
        skillVersionIds = skillVersionIds == null ? List.of() : List.copyOf(skillVersionIds);
        memoryIds = memoryIds == null ? List.of() : List.copyOf(memoryIds);
    }
}
