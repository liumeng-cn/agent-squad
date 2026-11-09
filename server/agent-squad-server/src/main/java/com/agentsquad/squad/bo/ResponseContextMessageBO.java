package com.agentsquad.squad.bo;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

/**
 * 已提交的会话消息材料。
 * <p>
 * 保留角色和版本，附件正文随冻结快照固定；截断材料保留原始大小与标记。
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ResponseContextMessageBO(String messageId, long sequence, String role, String text,
                                     long revision, List<String> artifactIds, List<ResponseContextMaterialBO> materials) {
    public ResponseContextMessageBO {
        if (materials == null) {
            materials = List.of();
        }
    }
}
