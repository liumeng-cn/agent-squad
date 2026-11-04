package com.agentsquad.squad.common.web;

import com.agentsquad.squad.common.model.ApiCode;
import com.agentsquad.squad.common.model.ApiResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ApiResult 序列化测试。
 */
class ApiResultTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void success_serializesCodeDataMsg() throws Exception {
        ApiResult<String> result = ApiResult.success("payload");
        String json = mapper.writeValueAsString(result);
        assertThat(json).contains("\"code\":0");
        assertThat(json).contains("\"data\":\"payload\"");
        assertThat(json).contains("\"msg\":\"成功\"");
    }

    @Test
    void error_serializesCodeAndMsg() throws Exception {
        ApiResult<Void> result = ApiResult.error(ApiCode.NOT_FOUND);
        String json = mapper.writeValueAsString(result);
        assertThat(json).contains("\"code\":404");
        assertThat(json).contains("\"msg\":\"资源不存在\"");
        assertThat(json).contains("\"data\":null");
    }

    @Test
    void error_usesCustomMsgAndFallsBackWhenBlank() throws Exception {
        assertThat(ApiResult.error(ApiCode.CONFLICT, "版本冲突").getMsg()).isEqualTo("版本冲突");
        assertThat(ApiResult.error(ApiCode.CONFLICT, " ").getMsg()).isEqualTo(ApiCode.CONFLICT.getMsg());
    }

    @Test
    void fromCode_fallsBackByStatusClass() {
        assertThat(ApiCode.fromCode(409)).isEqualTo(ApiCode.CONFLICT);
        assertThat(ApiCode.fromCode(418)).isEqualTo(ApiCode.BAD_REQUEST);
        assertThat(ApiCode.fromCode(502)).isEqualTo(ApiCode.INTERNAL_ERROR);
    }
}
