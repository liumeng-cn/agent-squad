package com.agentsquad.squad.v2.utils;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

/** V2 标识生成工具测试。 */
class SquadIdUtilsTest {
    @Test void uuidShouldNotContainHyphens() {
        String id = SquadIdUtils.uuidWithoutHyphens();
        assertEquals(32, id.length());
        assertFalse(id.contains("-"));
        assertTrue(id.matches("[0-9a-f]{32}"));
    }
    @Test void snowflakeIdsShouldBePositiveAndDistinct() {
        Long first = SquadIdUtils.nextSnowflakeId();
        Long second = SquadIdUtils.nextSnowflakeId();
        assertTrue(first > 0L);
        assertTrue(second > 0L);
        assertNotEquals(first, second);
    }
}