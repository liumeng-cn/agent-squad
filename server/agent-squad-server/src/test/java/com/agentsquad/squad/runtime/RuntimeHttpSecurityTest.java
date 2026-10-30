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
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.nio.file.Path;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * HTTP 边界测试：验证伪造身份头无效，Cookie 与 CSRF 缺一不可。
 */
class RuntimeHttpSecurityTest {

    @Test
    void shouldRequireAuthenticatedCookieAndCsrfForMutation(@TempDir Path tempDirectory) throws Exception {
        String databaseUrl = com.agentsquad.squad.testsupport.MySqlTestDatabase.url(tempDirectory.resolve("squad"));
        try (ConfigurableApplicationContext context = SpringApplication.run(AgentSquadApplication.class,
                "--server.port=0",
                "--spring.datasource.url=" + databaseUrl,
                "--spring.datasource.username=" + com.agentsquad.squad.testsupport.MySqlTestDatabase.username(), "--spring.datasource.password=" + com.agentsquad.squad.testsupport.MySqlTestDatabase.password(),
                "--spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver",
                "--agentsquad.auth.username=local-user",
                "--agentsquad.auth.password=local-password")) {
            MockMvc mvc = MockMvcBuilders.webAppContextSetup((WebApplicationContext) context).build();
            mvc.perform(post("/api/v1/conversations")
                            .header("X-D1-User", "forged-user")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"title\":\"test\"}"))
                    .andExpect(status().isUnauthorized());

            var login = mvc.perform(post("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"username\":\"local-user\",\"password\":\"local-password\"}"))
                    .andExpect(status().isOk()).andReturn();
            String session = login.getResponse().getCookie("SQUAD_SESSION").getValue();
            JsonNode auth = context.getBean(ObjectMapper.class).readTree(login.getResponse().getContentAsString());
            String csrf = auth.get("csrfToken").asText();

            mvc.perform(post("/api/v1/conversations")
                            .cookie(new Cookie("SQUAD_SESSION", session))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"title\":\"test\"}"))
                    .andExpect(status().isUnauthorized());
            mvc.perform(post("/api/v1/conversations")
                            .cookie(new Cookie("SQUAD_SESSION", session))
                            .header("X-CSRF-Token", csrf)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"title\":\"test\"}"))
                    .andExpect(status().isCreated());
            mvc.perform(get("/api/v1/commands/00000000-0000-0000-0000-000000000000")
                            .cookie(new Cookie("SQUAD_SESSION", session)))
                    .andExpect(status().isNotFound());
        }
    }
}
