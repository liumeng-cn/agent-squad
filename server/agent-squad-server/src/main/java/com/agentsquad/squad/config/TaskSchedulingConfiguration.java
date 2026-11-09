package com.agentsquad.squad.config;

import com.agentsquad.squad.agent.WorkerFactory;
import com.agentsquad.squad.poller.NativeProjectionPollTask;
import com.agentsquad.squad.scheduler.ArtifactRetentionTask;
import com.agentsquad.squad.scheduler.CancellationSettlementTask;
import com.agentsquad.squad.scheduler.ConversationCleanupTask;
import com.agentsquad.squad.scheduler.OperationUnknownSweepTask;
import com.agentsquad.squad.scheduler.SandboxRecoveryTask;
import com.agentsquad.squad.scheduler.TaskDirectiveScanTask;
import com.agentsquad.squad.service.CommandDeliveryService;
import com.agentsquad.squad.service.ConversationEventStreamerService;
import com.agentsquad.squad.service.LocalSessionStopper;
import com.agentsquad.squad.service.RedisCommandWakeup;
import com.agentsquad.squad.service.RedisConversationNotificationsService;
import com.agentsquad.squad.service.RedisProjectionWakeup;
import com.agentsquad.squad.service.RuntimeAdmissionService;
import com.agentsquad.squad.service.RuntimeProjectionDispatcher;
import com.agentsquad.squad.config.properties.RedisWakeupProperties;
import com.agentsquad.squad.config.properties.TaskScheduleProperties;
import jakarta.annotation.Resource;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;

import java.util.function.LongSupplier;

/**
 * 业务服务后台任务调度配置
 *
 * <p>按类型读取任务配置并注册已启用的任务，每轮结束后按当前间隔安排下一轮。</p>
 */
@Configuration
@EnableConfigurationProperties(TaskScheduleProperties.class)
public class TaskSchedulingConfiguration implements SchedulingConfigurer {

    @Resource
    private TaskScheduleProperties properties;

    @Resource
    private RedisWakeupProperties redisWakeupProperties;

    @Resource
    private ObjectProvider<ArtifactRetentionTask> artifactRetentionTaskProvider;

    @Resource
    private ObjectProvider<CancellationSettlementTask> cancellationSettlementTaskProvider;

    @Resource
    private ConversationCleanupTask conversationCleanupTask;

    @Resource
    private OperationUnknownSweepTask operationUnknownSweepTask;

    @Resource
    private ObjectProvider<SandboxRecoveryTask> sandboxRecoveryTaskProvider;

    @Resource
    private ObjectProvider<TaskDirectiveScanTask> taskDirectiveScanTaskProvider;

    @Resource
    private ObjectProvider<NativeProjectionPollTask> nativeProjectionPollTaskProvider;

    @Resource
    private ObjectProvider<WorkerFactory> workerFactoryProvider;

    @Resource
    private ObjectProvider<CommandDeliveryService> commandDeliveryServiceProvider;

    @Resource
    private ConversationEventStreamerService conversationEventStreamerService;

    @Resource
    private ObjectProvider<LocalSessionStopper> localSessionStopperProvider;

    @Resource
    private ObjectProvider<RedisCommandWakeup> redisCommandWakeupProvider;

    @Resource
    private ObjectProvider<RedisConversationNotificationsService> redisConversationNotificationsServiceProvider;

    @Resource
    private ObjectProvider<RedisProjectionWakeup> redisProjectionWakeupProvider;

    @Resource
    private RuntimeAdmissionService runtimeAdmissionService;

    @Resource
    private ObjectProvider<RuntimeProjectionDispatcher> runtimeProjectionDispatcherProvider;

    @Override
    public void configureTasks(ScheduledTaskRegistrar registrar) {
        artifactRetentionTaskProvider.ifAvailable(task ->
                register(registrar, task::purgeExpired, properties::getArtifactRetentionDelayMs));
        cancellationSettlementTaskProvider.ifAvailable(task ->
                register(registrar, task::settle, properties::getCancellationSettlementDelayMs));
        register(registrar, conversationCleanupTask::cleanupDeletingConversations, properties::getConversationCleanupDelayMs);
        register(registrar, operationUnknownSweepTask::sweep, properties::getOperationUnknownSweepDelayMs);
        sandboxRecoveryTaskProvider.ifAvailable(task ->
                register(registrar, task::markUnknown, properties::getSandboxRecoveryDelayMs));
        taskDirectiveScanTaskProvider.ifAvailable(task ->
                register(registrar, task::dispatchDue, properties::getTaskDirectiveScanDelayMs));
        nativeProjectionPollTaskProvider.ifAvailable(task ->
                register(registrar, task::projectDue, properties::getNativeProjectionPollDelayMs));
        workerFactoryProvider.ifAvailable(task ->
                register(registrar, task::pruneFinished, properties::getWorkerPruneDelayMs));
        commandDeliveryServiceProvider.ifAvailable(task ->
                register(registrar, task::deliverDue, this::commandDeliveryDelay));
        register(registrar, conversationEventStreamerService::reconcile, properties::getConversationEventReconcileDelayMs);
        localSessionStopperProvider.ifAvailable(task ->
                register(registrar, task::stopRequested, properties::getLocalSessionStopCheckDelayMs));
        redisCommandWakeupProvider.ifAvailable(task ->
                register(registrar, task::receive, properties::getRedisCommandPollDelayMs));
        redisConversationNotificationsServiceProvider.ifAvailable(task ->
                register(registrar, task::ensureSubscribed, properties::getRedisConversationReconnectDelayMs));
        redisProjectionWakeupProvider.ifAvailable(task ->
                register(registrar, task::receive, properties::getRedisProjectionPollDelayMs));
        register(registrar, runtimeAdmissionService::auditCounters, properties::getRuntimeAdmissionAuditDelayMs);
        runtimeProjectionDispatcherProvider.ifAvailable(task ->
                register(registrar, task::retryPending, properties::getProjectionWakeupRetryDelayMs));
    }

    private long commandDeliveryDelay() {
        return redisWakeupProperties.isEnabled()
                ? properties.getCommandDeliveryCompensationDelayMs()
                : properties.getCommandDeliveryPollDelayMs();
    }

    private void register(ScheduledTaskRegistrar registrar, Runnable task, LongSupplier delay) {
        registrar.addTriggerTask(task, context -> {
            var completion = context.lastCompletion();
            return completion == null ? context.getClock().instant() : completion.plusMillis(delay.getAsLong());
        });
    }
}
