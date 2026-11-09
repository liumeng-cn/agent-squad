package com.agentsquad.squad.runtime;

import com.agentsquad.squad.testsupport.PostgresTestDatabase;
import com.agentsquad.squad.AgentSquadApplication;
import com.agentsquad.squad.testsupport.ApiResultJson;
import com.agentsquad.squad.testsupport.TestAssertion;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
        String databaseUrl = PostgresTestDatabase.url(tempDirectory.resolve("squad"));
        try (ConfigurableApplicationContext context = SpringApplication.run(AgentSquadApplication.class,
                "--server.port=0",
                "--spring.datasource.url=" + databaseUrl,
                "--spring.datasource.username=" + PostgresTestDatabase.username(), "--spring.datasource.password=" + PostgresTestDatabase.password(),
                "--spring.datasource.driver-class-name=org.postgresql.Driver",
                "--agentsquad.auth.username=local-user",
                "--agentsquad.auth.password=local-password")) {
            MockMvc mvc = MockMvcBuilders.webAppContextSetup((WebApplicationContext) context).build();
            ObjectMapper mapper = context.getBean(ObjectMapper.class);


            var conversation = mvc.perform(post("/api/v1/conversations")
                            .with(TestAssertion.as(201L, "ADMIN"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"title\":\"快照测试\"}"))
                    .andExpect(status().isCreated()).andReturn();
            String conversationId = ApiResultJson.data(mapper, conversation.getResponse().getContentAsString())
                    .get("conversationId").asText();

            var receipt = mvc.perform(post("/api/v1/conversations/{conversationId}/tasks", conversationId)
                            .with(TestAssertion.as(201L, "ADMIN"))
                            .header("Idempotency-Key", "snapshot-test-1")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"message\":\"检查快照字段\"}"))
                    .andExpect(status().isAccepted()).andReturn();
            String taskId = ApiResultJson.data(mapper, receipt.getResponse().getContentAsString()).get("taskId").asText();

            MvcResult result = mvc.perform(get("/api/v1/tasks/{taskId}/snapshot", taskId)
                            .with(TestAssertion.as(201L, "ADMIN")))
                    .andExpect(status().isOk()).andReturn();
            JsonNode snapshot = ApiResultJson.data(mapper, result.getResponse().getContentAsString());

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
