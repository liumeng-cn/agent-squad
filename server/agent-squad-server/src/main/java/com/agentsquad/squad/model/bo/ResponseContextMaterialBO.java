package com.agentsquad.squad.model.bo;

/**
 * 回复上下文中的用户材料。
 * <p>
 * 冻结时读取附件正文并按预算截断；截断时记录完整大小，可审计。
 */
public record ResponseContextMaterialBO(String artifactId, String fileName, String mediaType,
                                        long sizeBytes, String sha256, String content, boolean truncated) {
}
