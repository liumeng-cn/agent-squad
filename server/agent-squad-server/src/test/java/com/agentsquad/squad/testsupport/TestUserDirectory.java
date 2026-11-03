package com.agentsquad.squad.testsupport;

import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 业务集成测试的账号目录。
 *
 * <p>C1 起业务安全点读取共享 `sq_user`；测试在启动隔离库后显式激活本用例使用的合成账号，
 * 不使用生产可切换的绕过开关。</p>
 */
public final class TestUserDirectory {

    private TestUserDirectory() {
    }

    public static void activate(ConfigurableApplicationContext context, String... userIds) {
        JdbcTemplate jdbc = context.getBean(JdbcTemplate.class);
        for (String userId : userIds) {
            if (userId == null || userId.isBlank()) {
                continue;
            }
            jdbc.update("INSERT INTO sq_user (user_id, username, display_name, password_hash, role, status, "
                            + "auth_version, must_change_password, creator_id, creator_name) "
                            + "VALUES (?, ?, ?, 'test-only', 'USER', 'ACTIVE', 0, 0, 'test', 'test') "
                            + "ON DUPLICATE KEY UPDATE status = 'ACTIVE', del_flag = '0'",
                    userId, "test-" + userId, userId);
        }
    }

    public static void disable(ConfigurableApplicationContext context, String userId) {
        context.getBean(JdbcTemplate.class).update(
                "UPDATE sq_user SET status = 'DISABLED' WHERE user_id = ?", userId);
    }
}
