package com.agentsquad.squad.v2;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * 聊天 HTTP 验证
 * <p>
 * 验证 MVC 对 Flux SSE 的实际适配与请求校验，模型未启用也必须明确结束回复。
 */
@SpringBootTest(properties = {"agentsquad.v2.agent.enabled=false", "agentsquad.v2.observation.enabled=false"})
@AutoConfigureMockMvc
class ChatHttpTest {
    @Autowired
    private MockMvc mvc;

    @Test
    void returnsSseFailureWhenModelIsDisabled() throws Exception {
        var result = mvc.perform(post("/api/v2/chat/messages")
                        .contentType("application/json").accept("text/event-stream")
                        .content("{\"sessionId\":\"session\",\"requestId\":\"request\",\"message\":\"你好\"}"))
                .andExpect(request().asyncStarted()).andReturn();
        mvc.perform(asyncDispatch(result)).andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/event-stream"))
                .andExpect(content().string(containsString("RUN_ERROR")));
    }

    @Test
    void rejectsEmptyMessageBeforeStartingStream() throws Exception {
        mvc.perform(post("/api/v2/chat/messages").contentType("application/json")
                        .content("{\"sessionId\":\"session\",\"requestId\":\"request\",\"message\":\" \"}"))
                .andExpect(status().isBadRequest());
    }
}
