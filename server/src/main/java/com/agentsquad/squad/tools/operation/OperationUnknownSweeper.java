package com.agentsquad.squad.tools.operation;

import com.agentsquad.squad.tools.operation.domain.OperationRow;
import com.agentsquad.squad.tools.operation.repository.OperationMapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

/**
 * 失联执行器扫描：IN_FLIGHT 超过租约仍未出账的操作统一转 UNKNOWN，
 * 既不判失败也不自动重发，后续只能由核对流程出账。
 */
@Component
@Slf4j
public class OperationUnknownSweeper {

    @Resource
    private OperationMapper operationMapper;

    @Value("${agentsquad.operation.unknown-sweep-batch:50}")
    private int batchSize;

    /**
     * 周期扫描租约过期的 IN_FLIGHT 操作并 CAS 转 UNKNOWN；版本竞争时跳过本轮。
     */
    @Scheduled(fixedDelayString = "${agentsquad.operation.unknown-sweep-delay-ms:15000}")
    public void sweep() {
        List<OperationRow> expired = operationMapper.findExpiredInFlight(
                LocalDateTime.now(ZoneOffset.UTC), batchSize);
        for (OperationRow row : expired) {
            if (operationMapper.markUnknown(row.userId(), row.operationId(),
                    row.version(), "unknown-sweeper") == 1) {
                log.warn("IN_FLIGHT 租约过期转 UNKNOWN operationId={} taskId={} owner={}",
                        row.operationId(), row.taskId(), row.executorOwner());
            }
        }
    }
}
