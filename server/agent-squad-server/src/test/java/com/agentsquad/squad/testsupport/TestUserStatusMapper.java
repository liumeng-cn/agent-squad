package com.agentsquad.squad.testsupport;

import com.agentsquad.squad.identity.UserStatusMapper;
import jakarta.annotation.Resource;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 业务集成测试的合成账号目录。
 *
 * <p>历史用例在隔离库中直接使用随机 userId，并未创建真实账号。测试目录对未登记 ID 视作 ACTIVE，
 * 但显式写入的 DISABLED 行仍按真实状态拒绝；生产构建不包含本类，也不存在运行时开关。</p>
 */
@Component
@Primary
public class TestUserStatusMapper implements UserStatusMapper {

    @Resource
    private JdbcTemplate jdbcTemplate;

    @Override
    public String findStatus(String userId) {
        List<String> statuses = jdbcTemplate.queryForList(
                "SELECT status FROM sq_user WHERE user_id = ? AND del_flag = '0'", String.class, userId);
        return statuses.isEmpty() ? "ACTIVE" : statuses.getFirst();
    }
}
