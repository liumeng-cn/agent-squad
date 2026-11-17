package com.agentsquad.squad.v2.po;

import com.agentsquad.squad.v2.enums.SessionExecutionStatus;
import com.agentsquad.squad.v2.enums.SessionTitleSource;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.Instant;

/**
 * 会话候选数据结构
 * <p>
 * 存储路线尚未确定，当前mock不使用该结构进行持久化
 */
@TableName(value = "sq_chat_session", schema = "squad_chat")
@Data
public class ChatSessionPO {
    /**
     * 内部存储主键
     */
    @TableId(type = IdType.AUTO)
    private Long id;
    /**
     * 业务会话ID
     */
    private String sessionId;
    /**
     * 归属用户业务ID
     */
    private Long userId;
    /**
     * 固定Agent标识
     */
    private String agentId;
    /**
     * 展示标题
     */
    private String title;
    /**
     * 自动或手动标题
     */
    private SessionTitleSource titleSource;
    /**
     * 标题编辑版本
     */
    private Long version;
    /**
     * 已确认接收过用户输入
     */
    private Boolean hasMessages;
    /**
     * 首条受理输入
     */
    private String firstInteractionId;
    /**
     * 待核对的首次输入
     */
    private String pendingInitialInputId;
    /**
     * 待确认的初始标题
     */
    private String pendingInitialTitle;
    /**
     * 最近新输入的原生受理时间
     */
    private Instant lastActivityTime;
    /**
     * 运行状态投影
     */
    private SessionExecutionStatus executionStatus;
    /**
     * 执行日志水位
     */
    private Long executionCursor;
    /**
     * 输入日志水位
     */
    private Long inputCursor;
    /**
     * 状态成功观察时间
     */
    private Instant executionObservedAt;
    /**
     * 上次核对尝试时间
     */
    private Instant lastStatusCheckAt;
    /**
     * 建档时间
     */
    private Instant createTime;
    /**
     * 修改时间
     */
    private Instant lastUpdateTime;
    /**
     * 删除标记
     */
    private String delFlag;
}
