package com.agentsquad.squad.identity;

import jakarta.annotation.Resource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/**
 * 用户禁用后的执行授权安全点。正式环境必须启用。
 * 账号禁用不删除任务与已发出外部副作用；只是阻断新的派发动作。
 */
@Service
public class UserStatusGuard {
    @Resource private JdbcTemplate jdbc;
    @Value("${agentsquad.identity.enforce-user-status:false}") private boolean enabled;

    public void requireActive(String userId) {
        if (!enabled) return;
        Integer count=jdbc.queryForObject(
                "SELECT COUNT(*) FROM sq_user WHERE user_id=? AND status='ACTIVE' AND del_flag='0'",
                Integer.class,userId);
        if(count==null || count!=1)
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,"用户已停用，禁止开始新执行");
    }
}
