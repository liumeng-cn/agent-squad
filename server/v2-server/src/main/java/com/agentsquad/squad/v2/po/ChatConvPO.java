package com.agentsquad.squad.v2.po;

import com.agentsquad.squad.v2.enums.ConvTitleSource;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.Instant;

/**
 * 对话目录持久化模型
 * <p>
 * 保存用户归属和标题等目录字段；消息与执行事实由 SDK 原生持久化，本表不重复保存。
 */
@Data
@TableName(value = "sq_chat_conv", schema = "squad_chat")
public class ChatConvPO {
    /**
     * 内部存储主键，不对外暴露
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 业务对话ID，客户端生成的幂等建档标识
     */
    private String convId;

    /**
     * 关联的原生会话ID，建档时生成后不变
     */
    private String sessionId;

    /**
     * 绑定的逻辑 Agent 标识，跨进程重启保持不变
     */
    private String agentDefinitionId;

    /**
     * 归属用户业务ID
     */
    private Long userId;

    /**
     * 展示标题
     */
    private String title;

    /**
     * 标题来源
     */
    private ConvTitleSource titleSource;

    /**
     * 标题编辑版本，改名按版本条件更新
     */
    private Long version;

    /**
     * 对话备注，暂不由页面编辑
     */
    private String remark;

    /**
     * 归档时间；未归档为 null，归档后从主列表移除
     */
    private Instant archivedAt;

    /**
     * 删除时间；未删除为 null，删除后所有入口按不存在处理
     */
    private Instant deletedAt;

    /**
     * 创建人ID
     */
    private Long creatorId;

    /**
     * 创建人姓名
     */
    private String creatorName;

    /**
     * 建档时间
     */
    private Instant createTime;

    /**
     * 最后更新人ID
     */
    private Long lastUpdaterId;

    /**
     * 最后更新人姓名
     */
    private String lastUpdaterName;

    /**
     * 最后更新时间
     */
    private Instant lastUpdateTime;
}
