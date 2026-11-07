package com.agentsquad.squad.tools.operation.service;

import com.agentsquad.squad.common.exception.RuntimeBusinessException;
import com.agentsquad.squad.common.exception.RuntimeExceptionCode;
import com.agentsquad.squad.conversation.service.ExecutionScopeService;
import com.agentsquad.squad.identity.service.UserStatusGuard;
import com.agentsquad.squad.domain.entity.OperationDO;
import com.agentsquad.squad.tools.operation.repository.OperationMapper;
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
    private UserStatusGuard userStatusGuard;

    /**
     * 独立事务领取操作；UNKNOWN或RECONCILING未核清前，同会话不得发出新的外部请求。
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int begin(OperationDO operation, String owner, long fence, LocalDateTime leaseUntil, String externalKey) {
        OperationDO current = mapper.findOwned(operation.userId(), operation.operationId());
        if (current == null || !java.util.Objects.equals(current.scopeId(), operation.scopeId())) {
            throw new RuntimeBusinessException(RuntimeExceptionCode.CONFLICT, "操作执行归属已变化");
        }
        userStatusGuard.requireActive(current.userId());
        scopeService.lockOwned(current.userId(), current.scopeId(), true);
        if (!mapper.findUncertainInConversation(current.userId(), current.scopeId()).isEmpty()) {
            throw new RuntimeBusinessException(RuntimeExceptionCode.CONFLICT, "已有外部操作结果待核对，暂停新的外部执行");
        }
        return mapper.markInFlight(operation.userId(), operation.operationId(), operation.version(),
                owner, fence, leaseUntil, externalKey);
    }
}
