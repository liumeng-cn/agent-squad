package com.agentsquad.squad.model.po;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * 隔离产物暂存记录。
 * <p>
 * 用于沙箱执行或产物传输，不接受宿主路径或模型提供的用户身份。
 */
@Getter
@Setter
@ToString
@EqualsAndHashCode
@NoArgsConstructor
@AllArgsConstructor
@TableName("sq_artifact_stage")
public class ArtifactStagePO {
    /**
     * 暂存产物业务ID
     */
    private String stageId;
    /**
     * 归属用户ID
     */
    private String userId;
    /**
     * 交付作用域ID
     */
    private String scopeId;
    /**
     * 来源绑定ID
     */
    private String bindingId;
    /**
     * 来源沙箱执行ID
     */
    private String executionId;
    /**
     * 来源隔离工作区相对路径
     */
    private String workspaceKey;
    /**
     * 安全展示文件名
     */
    private String fileName;
    /**
     * 固定文件SHA256摘要
     */
    private String sha256;
    /**
     * 暂存文件内容
     */
    private byte[] content;
    /**
     * 状态[STAGED,COMMITTED,EXPIRED]
     */
    private String status;
    /**
     * 已发布产物ID
     */
    private String artifactId;
}
