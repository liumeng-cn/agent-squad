package com.agentsquad.squad.config.properties;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 后台调度配置属性
 *
 * <p>配置业务服务的调度频率和扫描批量，时间单位为毫秒。</p>
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "agentsquad.scheduler")
public class TaskScheduleProperties {

    /** 过期产物清理间隔，单位毫秒 */
    private volatile long artifactRetentionDelayMs = 3600000;

    /** 取消收尾检查间隔，单位毫秒 */
    private volatile long cancellationSettlementDelayMs = 3000;

    /** 启用 Redis 唤醒时的命令补偿扫描间隔，单位毫秒 */
    private volatile long commandDeliveryCompensationDelayMs = 1000;

    /** 未启用 Redis 唤醒时的命令轮询间隔，单位毫秒 */
    private volatile long commandDeliveryPollDelayMs = 1000;

    /** 已到期未知命令的隔离扫描间隔，单位毫秒 */
    private volatile long commandUnknownSweepDelayMs = 15000;

    /** 待删除会话清理间隔，单位毫秒 */
    private volatile long conversationCleanupDelayMs = 2000;

    /** 会话事件订阅补查间隔，单位毫秒 */
    private volatile long conversationEventReconcileDelayMs = 15000;

    /** 本地会话停止状态检查间隔，单位毫秒 */
    private volatile long localSessionStopCheckDelayMs = 500;

    /** 原生事件投影轮询间隔，单位毫秒 */
    private volatile long nativeProjectionPollDelayMs = 1000;

    /** 每轮扫描的过期外部操作数量上限 */
    private volatile int operationUnknownSweepBatch = 50;

    /** 外部操作超时扫描间隔，单位毫秒 */
    private volatile long operationUnknownSweepDelayMs = 15000;

    /** 投影唤醒重试间隔，单位毫秒 */
    private volatile long projectionWakeupRetryDelayMs = 15000;

    /** Redis 命令唤醒队列轮询间隔，单位毫秒 */
    private volatile long redisCommandPollDelayMs = 200;

    /** 会话通知 Redis 订阅重连间隔，单位毫秒 */
    private volatile long redisConversationReconnectDelayMs = 5000;

    /** Redis 投影唤醒队列轮询间隔，单位毫秒 */
    private volatile long redisProjectionPollDelayMs = 200;

    /** 运行准入计数巡检间隔，单位毫秒 */
    private volatile long runtimeAdmissionAuditDelayMs = 60000;

    /** 失联沙箱执行核对间隔，单位毫秒 */
    private volatile long sandboxRecoveryDelayMs = 30000;

    /** 到期目标修订扫描间隔，单位毫秒 */
    private volatile long taskDirectiveScanDelayMs = 1000;

    /** 过期 Worker 缓存清理间隔，单位毫秒 */
    private volatile long workerPruneDelayMs = 60000;
}
