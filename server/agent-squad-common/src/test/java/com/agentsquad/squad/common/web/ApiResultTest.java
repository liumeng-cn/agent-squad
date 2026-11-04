package com.agentsquad.squad.common.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ApiResult 序列化测试。
 */
class ApiResultTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void success_serializesCodeDataMessage() throws Exception {
        ApiResult<String> result = ApiResult.success("payload");
        String json = mapper.writeValueAsString(result);
        assertThat(json).contains("\"code\":\"0\"");
        assertThat(json).contains("\"data\":\"payload\"");
        assertThat(json).contains("\"message\":null");
    }

    @Test
    void error_serializesCodeAndMessage() throws Exception {
        ApiResult<Void> result = ApiResult.error("USER_NOT_FOUND", "用户不存在");
        String json = mapper.writeValueAsString(result);
        assertThat(json).contains("\"code\":\"USER_NOT_FOUND\"");
        assertThat(json).contains("\"message\":\"用户不存在\"");
        assertThat(json).contains("\"data\":null");
    }
}
