package com.agentsquad.squad.v2;

import com.agentsquad.squad.common.bo.CurrentUserBO;
import com.agentsquad.squad.common.utils.IdentityAssertion;
import com.agentsquad.squad.v2.mapper.ChatConvMapper;
import io.agentscope.harness.agent.HarnessAgent;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 页面JSON接口验证
 * <p>
 * 验证空目录、未建档拒绝、认证和统一异常边界。
 */
@SpringBootTest(properties = {"agentsquad.v2.agent.enabled=false", "agentsquad.v2.observation.enabled=false",
        "agentsquad.identity.identity-key=test-internal-identity-key-long-enough"})
@AutoConfigureMockMvc
class ChatHttpTest {
    @MockitoBean
    private HarnessAgent agent;

    @MockitoBean
    private ChatConvMapper convMapper;

    @Resource
    private MockMvc mvc;

    @org.junit.jupiter.api.BeforeEach
    void emptyPage() {
        when(convMapper.selectPage(any(com.baomidou.mybatisplus.extension.plugins.pagination.Page.class), any()))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    private String identity(String method, String path) {
        CurrentUserBO user = new CurrentUserBO();
        user.setUserId("1");
        user.setSessionId("test-session");
        return IdentityAssertion.sign(user, "v2-server", method, path, "test-internal-identity-key-long-enough");
    }

    @Test
    void rejectsUnsignedIdentity() throws Exception {
        mvc.perform(get("/api/v2/chat/convs").header("X-User-Id", "1"))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value(401));
    }

    @Test
    void readsEmptyDirectoryAndRejectsUnknownConversation() throws Exception {
        String list = "/api/v2/chat/convs";
        mvc.perform(post(list + "/page").contentType("application/json").content("{}")
                        .header(IdentityAssertion.HEADER, identity("POST", list + "/page")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.records.length()").value(0));

        String path = list + "/unknown";
        mvc.perform(get(path).header(IdentityAssertion.HEADER, identity("GET", path)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(404));
    }

    @Test
    void rejectsUnknownConversationBeforeOpeningEventStream() throws Exception {
        String path = "/api/v2/chat/convs/unknown/events";
        mvc.perform(get(path).accept("text/event-stream")
                        .header(IdentityAssertion.HEADER, identity("GET", path)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(404));
    }

    @Test
    void rejectsEveryExecutionEntryForUnownedConversation() throws Exception {
        String path = "/api/v2/chat/convs/unknown/session";
        mvc.perform(get(path).header(IdentityAssertion.HEADER, identity("GET", path)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(404));

        assertUnownedPost("/api/v2/chat/messages",
                """
                        {"convId":"unknown","interactionId":"request","message":"验证"}
                        """);
        assertUnownedPost("/api/v2/chat/interruptions",
                """
                        {"convId":"unknown","runId":"run"}
                        """);
        assertUnownedPost("/api/v2/chat/resumptions",
                """
                        {"convId":"unknown","turnId":"turn"}
                        """);
        assertUnownedPost("/api/v2/chat/answers",
                """
                        {"convId":"unknown","turnId":"turn","requestId":"question","answer":"确认"}
                        """);
        assertUnownedDelete("/api/v2/chat/convs/unknown");
        assertUnownedPost("/api/v2/chat/convs/unknown/archive", "{}");
        assertUnownedPost("/api/v2/chat/convs/unknown/unarchive", null);
        org.mockito.Mockito.verifyNoInteractions(agent);
    }

    @Test
    void usesCommonValidationResponse() throws Exception {
        String path = "/api/v2/chat/messages";
        mvc.perform(post(path).header(IdentityAssertion.HEADER, identity("POST", path)).contentType("application/json")
                        .content("{\"convId\":\"conv\",\"interactionId\":\"request\",\"message\":\" \"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(400));

        path = "/api/v2/chat/answers";
        mvc.perform(post(path).header(IdentityAssertion.HEADER, identity("POST", path)).contentType("application/json")
                        .content("{\"convId\":\"conv\",\"turnId\":\"turn\",\"requestId\":\"question\",\"answer\":\" \"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(400));
    }

    private void assertUnownedPost(String path, String body) throws Exception {
        var request = post(path).header(IdentityAssertion.HEADER, identity("POST", path))
                .contentType("application/json");
        if (body != null) {
            request = request.content(body);
        }
        mvc.perform(request)
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(404));
    }

    private void assertUnownedDelete(String path) throws Exception {
        mvc.perform(delete(path).header(IdentityAssertion.HEADER, identity("DELETE", path)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(404));
    }
}
