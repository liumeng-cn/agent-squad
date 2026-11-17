package com.agentsquad.squad.v2;

import com.agentsquad.squad.common.bo.CurrentUserBO;
import com.agentsquad.squad.common.utils.IdentityAssertion;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 页面JSON接口验证
 *
 * 验证固定数据可读取、写接口仅回显，以及认证和统一异常边界。
 */
@SpringBootTest(properties = {"agentsquad.v2.agent.enabled=false", "agentsquad.v2.observation.enabled=false",
        "agentsquad.identity.identity-key=test-internal-identity-key-long-enough"})
@AutoConfigureMockMvc
class ChatHttpTest {
    @Autowired
    private MockMvc mvc;

    private String identity(String method, String path) {
        CurrentUserBO user = new CurrentUserBO(); user.setUserId("1"); user.setSessionId("test-session");
        return IdentityAssertion.sign(user, "v2-server", method, path, "test-internal-identity-key-long-enough");
    }

    @Test
    void rejectsUnsignedIdentity() throws Exception {
        mvc.perform(get("/api/v2/chat/sessions").header("X-User-Id", "1"))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value(401));
    }

    @Test
    void readsFixedSessionListAndMessages() throws Exception {
        String list = "/api/v2/chat/sessions";
        mvc.perform(get(list).header(IdentityAssertion.HEADER, identity("GET", list)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.items.length()").value(3))
                .andExpect(jsonPath("$.data.items[0].title").value("开始一次新的协作"));
        String path = list + "/demo-api";
        mvc.perform(get(path).header(IdentityAssertion.HEADER, identity("GET", path)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.sessionId").value("demo-api"))
                .andExpect(jsonPath("$.data.messages[0].content").value("接口设计需要关注什么"));
    }

    @Test
    void echoesCreateRenameAndSendWithoutMockState() throws Exception {
        String list = "/api/v2/chat/sessions";
        mvc.perform(post(list).header(IdentityAssertion.HEADER, identity("POST", list)).contentType("application/json")
                        .content("{\"sessionId\":\"preview-new\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.sessionId").value("preview-new"));
        String path = list + "/preview-new";
        mvc.perform(patch(path).header(IdentityAssertion.HEADER, identity("PATCH", path)).contentType("application/json")
                        .content("{\"title\":\"查看页面\",\"version\":0}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.title").value("查看页面"))
                .andExpect(jsonPath("$.data.version").value(1));
        mvc.perform(get(list).header(IdentityAssertion.HEADER, identity("GET", list)))
                .andExpect(jsonPath("$.data.items.length()").value(3));
    }

    @Test
    void usesCommonValidationResponse() throws Exception {
        String path = "/api/v2/chat/messages";
        mvc.perform(post(path).header(IdentityAssertion.HEADER, identity("POST", path)).contentType("application/json")
                        .content("{\"sessionId\":\"session\",\"interactionId\":\"request\",\"message\":\" \"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(400));
    }
}
