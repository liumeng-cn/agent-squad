package com.agentsquad.squad.tools.operation;

import com.agentsquad.squad.tools.operation.domain.OperationRow;
import com.agentsquad.squad.tools.operation.repository.OperationMapper;
import com.agentsquad.squad.conversation.service.ExecutionScopeService;
import com.agentsquad.squad.runtime.service.ExecutionFailureService;
import com.agentsquad.squad.runtime.domain.FailureClassification;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;

/**
 * 外部未知结果的持久公开边界。
 * <p>
 * CAS账本与结构化故障同事务写入，持有归属锁，不把发送异常判断为未执行，也不自动重发。
 */
@Service
public class OperationUnknownService {
    @Resource
    private ExecutionScopeService scopes;

    @Resource
    private OperationMapper operations;

    @Resource
    private ExecutionFailureService failures;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean mark(OperationRow operation, long expectedVersion, String actor) {
        var scope = scopes.lockOwned(operation.userId(), operation.scopeId(), false);
        if (operations.markUnknown(operation.userId(), operation.operationId(), expectedVersion, actor) != 1) {
            return false;
        }
        failures.record(scope, operation.bindingId(), "operation:" + operation.operationId(), new FailureClassification(
                "EXTERNAL_UNKNOWN", "OPERATION_RESULT_UNCONFIRMED", "OPERATOR", "有一项外部操作的结果尚未确认，系统会先核对，期间不会重复执行。"));
        return true;
    }
}
