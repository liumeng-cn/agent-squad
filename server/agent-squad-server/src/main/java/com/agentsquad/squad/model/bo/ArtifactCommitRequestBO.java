package com.agentsquad.squad.model.bo;

/**
 * 产物提交请求。
 * <p>
 * 仅指定已暂存产物及预期摘要，不接受任意工作空间路径或用户身份。
 */
public record ArtifactCommitRequestBO(String stageId, String expectedHash) {
}
