package com.agentsquad.squad.v2;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
    void returnsBusinessFailureWhenModelIsDisabled() throws Exception {
        mvc.perform(post("/api/v2/chat/messages")
                        .contentType("application/json")
                        .content("{\"sessionId\":\"session\",\"interactionId\":\"request\",\"message\":\"你好\"}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value(503));
    }

    @Test
    void rejectsEmptyMessageBeforeStartingStream() throws Exception {
        mvc.perform(post("/api/v2/chat/messages").contentType("application/json")
                        .content("{\"sessionId\":\"session\",\"interactionId\":\"request\",\"message\":\" \"}"))
                .andExpect(status().isBadRequest());
    }
}
