package com.agentsquad.squad.config;

import com.agentsquad.squad.config.properties.TaskScheduleProperties;
import com.agentsquad.squad.scheduler.AuthSessionCleanupTask;
import jakarta.annotation.Resource;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;


/**
 * 网关后台任务调度配置
 *
 * <p>使用认证清理的启动延迟注册任务，此后按上一轮完成时间计算下次执行。</p>
 */
@Configuration
@EnableConfigurationProperties(TaskScheduleProperties.class)
public class GatewayTaskSchedulingConfiguration implements SchedulingConfigurer {

    @Resource
    private TaskScheduleProperties properties;

    @Resource
    private AuthSessionCleanupTask authSessionCleanupTask;

    @Override
    public void configureTasks(ScheduledTaskRegistrar registrar) {
        registrar.addTriggerTask(authSessionCleanupTask::sweep, context -> {
            var completion = context.lastCompletion();
            return completion == null
                    ? context.getClock().instant().plusMillis(properties.getAuthCleanupInitialDelayMs())
                    : completion.plusMillis(properties.getAuthCleanupDelayMs());
        });
    }
}
