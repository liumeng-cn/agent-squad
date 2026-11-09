package com.agentsquad.squad.service;

import com.agentsquad.squad.common.exception.RuntimeBusinessException;
import com.agentsquad.squad.domain.enums.RuntimeExceptionCode;
import com.agentsquad.squad.domain.entity.OperationDO;
import com.agentsquad.squad.mapper.OperationMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 外部派发前的独立持久闸门。
 * <p>
 * 按会话与执行所有者串行校验停止、代际和未知写入，再提交IN_FLIGHT；外部I/O必须发生在事务完成后。
 */
@Service
public class OperationDispatchService {

    @Resource
    private ExecutionScopeService scopeService;

    @Resource
    private OperationMapper mapper;

    @Resource
    private UserStatusGuardService userStatusGuard;

    /**
     * 独立事务领取操作；UNKNOWN或RECONCILING未核清前，同会话不得发出新的外部请求。
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int begin(OperationDO operation, String owner, long fence, LocalDateTime leaseUntil, String externalKey) {
        OperationDO current = mapper.findOwned(operation.getUserId(), operation.getOperationId());
        if (current == null || !java.util.Objects.equals(current.getScopeId(), operation.getScopeId())) {
            throw new RuntimeBusinessException(RuntimeExceptionCode.CONFLICT, "操作执行归属已变化");
        }
        userStatusGuard.requireActive(current.getUserId());
        scopeService.lockOwned(current.getUserId(), current.getScopeId(), true);
        if (!mapper.findUncertainInConversation(current.getUserId(), current.getScopeId()).isEmpty()) {
            throw new RuntimeBusinessException(RuntimeExceptionCode.CONFLICT, "已有外部操作结果待核对，暂停新的外部执行");
        }
        return mapper.markInFlight(operation.getUserId(), operation.getOperationId(), operation.getVersion(),
                owner, fence, leaseUntil, externalKey);
    }
}
