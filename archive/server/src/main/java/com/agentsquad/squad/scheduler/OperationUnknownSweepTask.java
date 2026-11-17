package com.agentsquad.squad.scheduler;

import com.agentsquad.squad.config.properties.TaskScheduleProperties;


import com.agentsquad.squad.service.OperationUnknownService;


import com.agentsquad.squad.model.po.OperationPO;
import com.agentsquad.squad.mapper.OperationMapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

/**
 * 外部操作超时扫描定时任务
 *
 * <p>识别租约过期且执行结果尚未确认的外部操作。</p>
 */
@Component
@Slf4j
public class OperationUnknownSweepTask {

    @Resource
    private OperationUnknownService unknowns;

    @Resource
    private OperationMapper operationMapper;

    @Resource
    private TaskScheduleProperties taskScheduleProperties;

    /**
     * 分批扫描租约过期的执行中操作，按版本条件标记为未知，遇到并发状态变更时跳过。
     */
    public void sweep() {
        List<OperationPO> expired = operationMapper.findExpiredInFlight(
                LocalDateTime.now(ZoneOffset.UTC), taskScheduleProperties.getOperationUnknownSweepBatch());
        for (OperationPO row : expired) {
            if (unknowns.mark(row, row.getVersion(), OperationUnknownService.SYSTEM_ACTOR_ID)) {
                log.warn("IN_FLIGHT 租约过期转 UNKNOWN operationId={} taskId={} owner={}",
                        row.getOperationId(), row.getTaskId(), row.getExecutorOwner());
            }
        }
    }
}
