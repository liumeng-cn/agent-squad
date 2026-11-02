package com.agentsquad.squad.identity;

import jakarta.annotation.Resource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/**
 * 用户执行权限状态校验。
 *
 * 在新请求、新执行及长期订阅的安全点重新查询账户状态，
 * 账号禁用后阻止后续执行与事件推送，不改变已提交的外部副作用。
 */
@Service
public class UserStatusGuard {

    @Resource
    private JdbcTemplate jdbc;

    @Value("${agentsquad.identity.enforce-user-status:false}")
    private boolean enabled;

    public void requireActive(String userId) {
        if (!enabled) {
            return;
        }
        if (userId == null || userId.isBlank()) {
            throw forbidden();
        }

        Integer activeCount = jdbc.queryForObject("""
                SELECT COUNT(*)
                FROM sq_user
                WHERE user_id = ?
                  AND status = 'ACTIVE'
                  AND del_flag = '0'
                """, Integer.class, userId);
        if (activeCount == null || activeCount != 1) {
            throw forbidden();
        }
    }

    private ResponseStatusException forbidden() {
        return new ResponseStatusException(HttpStatus.FORBIDDEN, "用户已停用，禁止继续访问");
    }
}
