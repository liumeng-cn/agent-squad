package com.agentsquad.squad.memory;

import com.agentsquad.squad.AgentSquadApplication;

import com.agentsquad.squad.memory.dto.MemoryCreate;
import com.agentsquad.squad.memory.service.MemoryService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.SpringApplication;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.nio.file.Path;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 记忆设置HTTP契约与用户隔离验证。
 * <p>
 * 覆盖网关身份缺失、跨用户资源、乐观冲突及墓碑响应，不向页面暴露持久对象和内部路径。
 */
class MemoryHttpTest {
    @Test
    void enforcesIdentityAndConflictAtManagementBoundary(@TempDir Path directory) throws Exception {
        String user = UUID.randomUUID().toString();
        try (var context = SpringApplication.run(AgentSquadApplication.class, "--server.port=0",
                "--spring.datasource.url=" + com.agentsquad.squad.testsupport.MySqlTestDatabase.url(directory.resolve("http")),
                "--spring.datasource.username=" + com.agentsquad.squad.testsupport.MySqlTestDatabase.username(), "--spring.datasource.password=" + com.agentsquad.squad.testsupport.MySqlTestDatabase.password(), "--spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver",
                "--agentsquad.delivery.enabled=false", "--agentsquad.auth.username=tester", "--agentsquad.auth.password=test-only",
                "--agentsquad.auth.user-id=" + user, "--agentsquad.auth.role=USER")) {
            MockMvc mvc = MockMvcBuilders.webAppContextSetup((WebApplicationContext) context).build();
            String userId = context.getEnvironment().getProperty("agentsquad.auth.user-id");
            mvc.perform(get("/api/v1/memories")).andExpect(status().isUnauthorized());
            mvc.perform(post("/api/v1/memories").contentType(MediaType.APPLICATION_JSON)
                    .content("{\"key\":\"language\",\"content\":\"中文\"}")).andExpect(status().isUnauthorized());
            String response = mvc.perform(post("/api/v1/memories").header("X-User-Id", userId).header("X-User-Role", "USER")
                            .contentType(MediaType.APPLICATION_JSON).content("{\"key\":\"language\",\"content\":\"中文\"}"))
                    .andExpect(status().isCreated()).andExpect(jsonPath("$.memory.kind").value("EXPLICIT"))
                    .andExpect(jsonPath("$.memory.version").value(0)).andReturn().getResponse().getContentAsString();
            String id = context.getBean(com.fasterxml.jackson.databind.ObjectMapper.class).readTree(response).path("memory").path("memoryId").asText();
            mvc.perform(patch("/api/v1/memories/{id}", id).header("X-User-Id", userId).header("X-User-Role", "USER")
                            .contentType(MediaType.APPLICATION_JSON).content("{\"content\":\"不能省略版本\"}"))
                    .andExpect(status().isBadRequest());
            mvc.perform(patch("/api/v1/memories/{id}", id).header("X-User-Id", userId).header("X-User-Role", "USER")
                            .contentType(MediaType.APPLICATION_JSON).content("{\"expectedVersion\":0,\"content\":\"简体中文\"}"))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.memory.version").value(1));
            mvc.perform(patch("/api/v1/memories/{id}", id).header("X-User-Id", userId).header("X-User-Role", "USER")
                            .contentType(MediaType.APPLICATION_JSON).content("{\"expectedVersion\":0,\"content\":\"不能覆盖\"}"))
                    .andExpect(status().isConflict());
            String foreign = context.getBean(MemoryService.class).create(UUID.randomUUID().toString(), new MemoryCreate("foreign", "secret", null)).memory().memoryId();
            mvc.perform(delete("/api/v1/memories/{id}", foreign).param("expectedVersion", "0").header("X-User-Id", userId).header("X-User-Role", "USER"))
                    .andExpect(status().isNotFound());
            mvc.perform(delete("/api/v1/memories/{id}", id).param("expectedVersion", "1").header("X-User-Id", userId).header("X-User-Role", "USER"))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.memory.status").value("DELETED"))
                    .andExpect(jsonPath("$.memory.content").value(""));
            mvc.perform(get("/api/v1/memories").header("X-User-Id", userId).header("X-User-Role", "USER")).andExpect(status().isOk())
                    .andExpect(jsonPath("$.items.length()").value(1));
        }
    }
}
