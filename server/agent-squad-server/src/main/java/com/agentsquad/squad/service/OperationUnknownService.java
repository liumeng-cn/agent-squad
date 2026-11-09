package com.agentsquad.squad.service;

import com.agentsquad.squad.po.OperationPO;
import com.agentsquad.squad.mapper.OperationMapper;
import com.agentsquad.squad.bo.FailureClassificationBO;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;

/**
 * 保存结果未知的外部操作，并更新用户可见状态
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
    public boolean mark(OperationPO operation, long expectedVersion, String actor) {
        var scope = scopes.lockOwned(operation.getUserId(), operation.getScopeId(), false);
        if (operations.markUnknown(operation.getUserId(), operation.getOperationId(), expectedVersion, actor) != 1) {
            return false;
        }
        failures.record(scope, operation.getBindingId(), "operation:" + operation.getOperationId(), new FailureClassificationBO(
                "EXTERNAL_UNKNOWN", "OPERATION_RESULT_UNCONFIRMED", "OPERATOR", "有一项外部操作的结果尚未确认，系统会先核对，期间不会重复执行。"));
        return true;
    }
}
