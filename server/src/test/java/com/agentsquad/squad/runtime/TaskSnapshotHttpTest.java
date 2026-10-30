package com.agentsquad.squad.runtime;

import com.agentsquad.squad.AgentSquadApplication;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 任务快照 HTTP 边界测试：验证快照端点返回与 GoalTask 契约对齐的字段及事件游标。
 */
class TaskSnapshotHttpTest {

    @Test
    void shouldReturnTaskSnapshotWithCursorAndEmptyJobs(@TempDir Path tempDirectory) throws Exception {
        String databaseUrl = com.agentsquad.squad.testsupport.MySqlTestDatabase.url(tempDirectory.resolve("squad"));
        try (ConfigurableApplicationContext context = SpringApplication.run(AgentSquadApplication.class,
                "--server.port=0",
                "--spring.datasource.url=" + databaseUrl,
                "--spring.datasource.username=" + com.agentsquad.squad.testsupport.MySqlTestDatabase.username(), "--spring.datasource.password=" + com.agentsquad.squad.testsupport.MySqlTestDatabase.password(),
                "--spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver",
                "--agentsquad.auth.username=local-user",
                "--agentsquad.auth.password=local-password")) {
            MockMvc mvc = MockMvcBuilders.webAppContextSetup((WebApplicationContext) context).build();
            ObjectMapper mapper = context.getBean(ObjectMapper.class);

            var login = mvc.perform(post("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"username\":\"local-user\",\"password\":\"local-password\"}"))
                    .andExpect(status().isOk()).andReturn();
            String session = login.getResponse().getCookie("SQUAD_SESSION").getValue();
            String csrf = mapper.readTree(login.getResponse().getContentAsString()).get("csrfToken").asText();

            var conversation = mvc.perform(post("/api/v1/conversations")
                            .cookie(new Cookie("SQUAD_SESSION", session))
                            .header("X-CSRF-Token", csrf)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"title\":\"快照测试\"}"))
                    .andExpect(status().isCreated()).andReturn();
            String conversationId = mapper.readTree(conversation.getResponse().getContentAsString())
                    .get("conversationId").asText();

            var receipt = mvc.perform(post("/api/v1/conversations/{conversationId}/tasks", conversationId)
                            .cookie(new Cookie("SQUAD_SESSION", session))
                            .header("X-CSRF-Token", csrf)
                            .header("Idempotency-Key", "snapshot-test-1")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"message\":\"检查快照字段\"}"))
                    .andExpect(status().isAccepted()).andReturn();
            String taskId = mapper.readTree(receipt.getResponse().getContentAsString()).get("taskId").asText();

            MvcResult result = mvc.perform(get("/api/v1/tasks/{taskId}/snapshot", taskId)
                            .cookie(new Cookie("SQUAD_SESSION", session)))
                    .andExpect(status().isOk()).andReturn();
            JsonNode snapshot = mapper.readTree(result.getResponse().getContentAsString());

            JsonNode task = snapshot.get("task");
            assertThat(task.get("taskId").asText()).isEqualTo(taskId);
            assertThat(task.get("conversationId").asText()).isEqualTo(conversationId);
            assertThat(task.get("status").asText()).isEqualTo("QUEUED");
            assertThat(task.get("version").asLong()).isEqualTo(0L);
            assertThat(task.get("planVersion").asLong()).isEqualTo(0L);
            assertThat(task.get("answerRevision").asLong()).isEqualTo(0L);
            assertThat(task.get("createdAt")).isNotNull();
            assertThat(task.get("updatedAt")).isNotNull();

            assertThat(snapshot.get("goalMessage").asText()).isEqualTo("检查快照字段");
            assertThat(snapshot.get("jobs")).isEmpty();
            assertThat(snapshot.get("pending")).isEmpty();
            assertThat(snapshot.get("answer").get("revision").asLong()).isEqualTo(0L);
            assertThat(snapshot.get("answer").get("completed").asBoolean()).isFalse();
            assertThat(snapshot.get("cursor").asText()).isEqualTo(taskId + ":1");
        }
    }
}
