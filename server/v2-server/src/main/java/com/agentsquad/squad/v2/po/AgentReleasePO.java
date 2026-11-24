package com.agentsquad.squad.v2.po;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.Instant;

/**
 * Master 发布定格快照
 * <p>
 * 映射 sq_agent_release，仅 Master 发布时创建，定格当次发布的全链路版本图。
 */
@Data
@TableName(value = "sq_agent_release", schema = "squad_chat")
public class AgentReleasePO {
    /**
     * 内部主键
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 发布快照业务ID
     */
    private Long releaseId;

    /**
     * 所属 Master 业务ID
     */
    private Long masterAgentId;

    /**
     * 发布序号
     */
    private Integer releaseNo;

    /**
     * 定格的 Master 配置版本ID
     */
    private Long masterVersionId;

    /**
     * 发布时定格的 Master 名称
     */
    private String masterName;

    /**
     * 发布时定格的 Master 展示别称
     */
    private String masterDisplayAlias;

    /**
     * 发布定格的全链路版本摘要指纹
     */
    private String dependencyDigest;

    /**
     * 发布操作幂等标识
     */
    private String publishOperationId;

    /**
     * 发布定格时间
     */
    @TableField(insertStrategy = FieldStrategy.NEVER)
    private Instant publishedAt;

    /**
     * 创建人ID
     */
    private Long creatorId;

    /**
     * 更新人ID
     */
    private Long lastUpdaterId;
}
