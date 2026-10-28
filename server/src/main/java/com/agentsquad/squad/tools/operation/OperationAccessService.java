package com.agentsquad.squad.tools.operation;

import com.agentsquad.squad.identity.AuthView;
import com.agentsquad.squad.tools.operation.repository.OperationAccessMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

/**
 * 运维核对的显式授权判定。
 * <p>
 * 同时要求 OPERATOR 角色及当前执行作用域的单独授权；ADMIN 不隐含运维权限，授权不扩大资源归属。
 */
@Service
public class OperationAccessService {
    @Resource
    private OperationAccessMapper mapper;

    public boolean canReconcile(AuthView actor, String scopeId, String status) {
        return "UNKNOWN".equals(status) && actor.roles().contains("OPERATOR")
                && mapper.countGrant(actor.userId(), scopeId) > 0;
    }
}
