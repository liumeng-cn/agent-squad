package com.agentsquad.squad.identity;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 用户活动状态安全点验证。
 *
 * 关闭状态校验时不访问数据库；开启后只接受唯一有效 ACTIVE 账号。
 */
class UserStatusGuardTest {

    private static final String USER_ID = "11111111-1111-1111-1111-111111111111";

    @Test
    void disabledCheckDoesNotQueryDatabase() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        UserStatusGuard guard = guard(jdbc, false);

        guard.requireActive(USER_ID);

        verifyNoInteractions(jdbc);
    }

    @Test
    void activeUserIsAllowed() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForObject(anyString(), eq(Integer.class), eq(USER_ID))).thenReturn(1);
        UserStatusGuard guard = guard(jdbc, true);

        guard.requireActive(USER_ID);
    }

    @Test
    void disabledOrMissingUserIsForbidden() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForObject(anyString(), eq(Integer.class), eq(USER_ID))).thenReturn(0);
        UserStatusGuard guard = guard(jdbc, true);

        assertForbidden(guard, USER_ID);
    }

    @Test
    void blankUserIsForbiddenWithoutQuery() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        UserStatusGuard guard = guard(jdbc, true);

        assertForbidden(guard, " ");
        verifyNoInteractions(jdbc);
    }

    private void assertForbidden(UserStatusGuard guard, String userId) {
        assertThatThrownBy(() -> guard.requireActive(userId))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        exception -> org.assertj.core.api.Assertions
                                .assertThat(exception.getStatusCode().value()).isEqualTo(403));
    }

    private UserStatusGuard guard(JdbcTemplate jdbc, boolean enabled) {
        UserStatusGuard guard = new UserStatusGuard();
        ReflectionTestUtils.setField(guard, "jdbc", jdbc);
        ReflectionTestUtils.setField(guard, "enabled", enabled);
        return guard;
    }
}
