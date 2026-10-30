package com.agentsquad.squad.skill;

import com.agentsquad.squad.AgentSquadApplication;
import com.agentsquad.squad.common.exception.RuntimeBusinessException;
import com.agentsquad.squad.conversation.dto.MessageCreateRequest;
import com.agentsquad.squad.conversation.service.MessageAcceptanceService;
import com.agentsquad.squad.skill.domain.SkillCatalog;
import com.agentsquad.squad.skill.dto.SkillFile;
import com.agentsquad.squad.skill.dto.SkillGrantRequest;
import com.agentsquad.squad.skill.dto.SkillImportRequest;
import com.agentsquad.squad.skill.integration.ScopeSkillRepository;
import com.agentsquad.squad.skill.service.SkillRuntimeService;
import com.agentsquad.squad.skill.service.SkillService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.harness.agent.skill.SkillResources;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 个人技能 HTTP、版本固定与撤销的持久化验收。
 * <p>
 * 使用正式消息 Scope 与 SDK 技能仓库；验证两用户同名隔离、空目录固定、停用与安全撤销的不同语义。
 */
class SkillHttpIntegrationTest {

    private static final String USER = "20000000-0000-0000-0000-000000000001";

    private static final String OTHER = "20000000-0000-0000-0000-000000000002";

    @Test
    void importsOnlyOwnedVersionsAndPinsActualSdkResources(@TempDir Path directory) throws Exception {
        try (ConfigurableApplicationContext context = start(directory)) {
            MockMvc mvc = MockMvcBuilders.webAppContextSetup((WebApplicationContext) context).build();
            ObjectMapper json = context.getBean(ObjectMapper.class);
            MvcResult login = mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                            .content("{\"username\":\"local-user\",\"password\":\"local-password\"}"))
                    .andExpect(status().isOk()).andReturn();
            Cookie cookie = login.getResponse().getCookie("SQUAD_SESSION");
            String csrf = json.readTree(login.getResponse().getContentAsString()).get("csrfToken").asText();
            SkillService skills = context.getBean(SkillService.class);
            SkillRuntimeService runtime = context.getBean(SkillRuntimeService.class);
            MessageAcceptanceService messages = context.getBean(MessageAcceptanceService.class);
            JdbcTemplate jdbc = context.getBean(JdbcTemplate.class);

            mvc.perform(get("/api/v1/skills")).andExpect(status().isUnauthorized());
            mvc.perform(post("/api/v1/skills").cookie(cookie).contentType(MediaType.APPLICATION_JSON)
                            .content(json.writeValueAsString(request("1.0.0", "旧资源"))))
                    .andExpect(status().isUnauthorized());
            mvc.perform(post("/api/v1/skills").cookie(cookie).header("X-CSRF-Token", csrf)
                            .contentType(MediaType.APPLICATION_JSON).content("{\"files\":[],\"userId\":\"forged\"}"))
                    .andExpect(status().isBadRequest());

            String imported = mvc.perform(post("/api/v1/skills").cookie(cookie).header("X-CSRF-Token", csrf)
                            .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(request("1.0.0", "旧资源"))))
                    .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
            JsonNode created = json.readTree(imported);
            String versionId = created.get("skillVersionId").asText();
            assertThat(created.get("status").asText()).isEqualTo("DISABLED");
            assertThat(skills.importPackage(USER, request("1.0.0", "旧资源")).skillVersionId()).isEqualTo(versionId);
            assertThatThrownBy(() -> skills.importPackage(USER, request("1.0.0", "不同资源")))
                    .hasMessageContaining("内容不同");
            String otherVersion = skills.importPackage(OTHER, request("1.0.0", "其他用户资源")).skillVersionId();
            mvc.perform(get("/api/v1/skills/{id}", otherVersion).cookie(cookie)).andExpect(status().isNotFound());
            assertThat(skills.list(USER, null, 10).items()).hasSize(1);
            assertThatThrownBy(() -> skills.detail(OTHER, versionId)).isInstanceOf(RuntimeBusinessException.class);

            var emptyReceipt = messages.accept(USER, null, "empty-catalog", new MessageCreateRequest("普通回复", List.of()));
            String emptyScope = scope(jdbc, emptyReceipt.executionId());
            assertThat(runtime.freeze(USER, emptyScope).skills()).isEmpty();
            mvc.perform(put("/api/v1/skills/{id}/grant", versionId).cookie(cookie).header("X-CSRF-Token", csrf)
                            .contentType(MediaType.APPLICATION_JSON).content("{\"expectedVersion\":null,\"enabled\":true}"))
                    .andExpect(status().isBadRequest());
            mvc.perform(put("/api/v1/skills/{id}/grant", versionId).cookie(cookie).header("X-CSRF-Token", csrf)
                            .contentType(MediaType.APPLICATION_JSON).content("{\"expectedVersion\":0,\"enabled\":null}"))
                    .andExpect(status().isBadRequest());
            mvc.perform(post("/api/v1/skills/{id}/revoke", versionId).cookie(cookie).header("X-CSRF-Token", csrf)
                            .contentType(MediaType.APPLICATION_JSON).content("{\"expectedVersion\":null}"))
                    .andExpect(status().isBadRequest());
            mvc.perform(put("/api/v1/skills/{id}/grant", versionId).cookie(cookie).header("X-CSRF-Token", csrf)
                            .contentType(MediaType.APPLICATION_JSON).content("{\"expectedVersion\":0,\"enabled\":true}"))
                    .andExpect(status().isOk());
            assertThat(runtime.freeze(USER, emptyScope).skills()).isEmpty();

            var receipt = messages.accept(USER, null, "fixed-v1", new MessageCreateRequest("使用写作技能", List.of()));
            String scopeId = scope(jdbc, receipt.executionId());
            SkillCatalog fixed = runtime.freeze(USER, scopeId);
            assertThat(fixed.skills()).extracting("skillVersionId").containsExactly(versionId);
            String sessionId = jdbc.queryForObject("SELECT framework_session_id FROM sq_runtime_binding WHERE scope_id = ?",
                    String.class, scopeId);
            RuntimeContext runtimeContext = RuntimeContext.builder().userId(USER).sessionId(sessionId).build();
            ScopeSkillRepository repository = context.getBean(ScopeSkillRepository.class);
            assertThat(repository.getAllSkills()).isEmpty();
            assertThat(repository.getAllSkills(RuntimeContext.empty())).isEmpty();
            assertThat(repository.getAllSkills(runtimeContext)).singleElement().satisfies(skill -> {
                assertThat(skill.getSource()).isEqualTo("platform-skill:" + versionId);
                assertThat(skill.getSkillContent()).contains("使用流程");
                assertThat(skill.getOriginDir()).isEmpty();
                assertThat(skill.getResources()).isEmpty();
            });
            SkillResources lazy = repository.resourcesFor("writer", runtimeContext);
            assertThat(lazy.read("references/guide.md")).contains("旧资源");
            assertThat(repository.getSkills(runtimeContext, Set.of())).isEmpty();

            skills.changeGrant(USER, versionId, new SkillGrantRequest(false, 1L));
            assertThat(runtime.read(USER, scopeId, versionId, "references/guide.md").content()).isEqualTo("旧资源");
            String newVersion = skills.importPackage(USER, request("2.0.0", "新资源")).skillVersionId();
            skills.changeGrant(USER, newVersion, new SkillGrantRequest(true, 0L));
            assertThat(runtime.freeze(USER, scopeId).skills()).extracting("skillVersionId").containsExactly(versionId);
            var newer = messages.accept(USER, null, "fixed-v2", new MessageCreateRequest("新执行", List.of()));
            assertThat(runtime.freeze(USER, scope(jdbc, newer.executionId())).skills())
                    .extracting("skillVersionId").containsExactly(newVersion);
            assertThatThrownBy(() -> runtime.read(OTHER, scopeId, versionId, "SKILL.md"))
                    .isInstanceOf(RuntimeBusinessException.class);

            mvc.perform(post("/api/v1/skills/{id}/revoke", versionId).cookie(cookie).header("X-CSRF-Token", csrf)
                            .contentType(MediaType.APPLICATION_JSON).content("{\"expectedVersion\":2}"))
                    .andExpect(status().isOk());
            assertThatThrownBy(() -> lazy.read("references/guide.md")).hasMessageContaining("SKILL_REVOKED");
            assertThatThrownBy(() -> runtime.validate(USER, scopeId)).hasMessageContaining("SKILL_REVOKED");
            assertThatThrownBy(() -> skills.changeGrant(USER, versionId, new SkillGrantRequest(true, 3L)))
                    .hasMessageContaining("已安全撤销");
        }
    }

    @Test
    void missingToolsDoNotBecomeImplicitAuthorization(@TempDir Path directory) {
        try (ConfigurableApplicationContext context = start(directory)) {
            SkillService skills = context.getBean(SkillService.class);
            var request = new SkillImportRequest(List.of(new SkillFile("SKILL.md",
                    "---\nname: needs-write\nversion: 1.0.0\ndescription: 需要外部工具\nrequires-tools: [restricted_write]\n---\n执行写入。")));
            String version = skills.importPackage(USER, request).skillVersionId();
            skills.changeGrant(USER, version, new SkillGrantRequest(true, 0L));
            var receipt = context.getBean(MessageAcceptanceService.class).accept(USER, null, "restricted",
                    new MessageCreateRequest("测试受限工具", List.of()));
            String scope = scope(context.getBean(JdbcTemplate.class), receipt.executionId());
            SkillRuntimeService runtime = context.getBean(SkillRuntimeService.class);
            runtime.freeze(USER, scope);
            assertThatThrownBy(() -> runtime.requireCapabilities(USER, scope, version, Set.of("mock_lookup")))
                    .hasMessageContaining("CAPABILITY_UNAVAILABLE");
            runtime.requireCapabilities(USER, scope, version, Set.of("restricted_write"));
        }
    }

    @Test
    void repeatableReadDoesNotSelectGrantDisabledAfterSnapshot(@TempDir Path directory) {
        try (ConfigurableApplicationContext context = start(directory)) {
            SkillService skills = context.getBean(SkillService.class);
            SkillRuntimeService runtime = context.getBean(SkillRuntimeService.class);
            JdbcTemplate jdbc = context.getBean(JdbcTemplate.class);
            String version = skills.importPackage(USER, request("1.0.0", "需停用的资源")).skillVersionId();
            skills.changeGrant(USER, version, new SkillGrantRequest(true, 0L));
            var message = context.getBean(MessageAcceptanceService.class).accept(USER, null, "rr-disable",
                    new MessageCreateRequest("冻结前有授权变化", List.of()));
            String scopeId = scope(jdbc, message.executionId());
            TransactionTemplate outer = repeatableRead(context);
            TransactionTemplate independent = independent(context);

            SkillCatalog result = outer.execute(status -> {
                jdbc.queryForObject("SELECT COUNT(*) FROM sq_skill_version WHERE user_id = ?", Integer.class, USER);
                independent.execute(inner -> {
                    skills.changeGrant(USER, version, new SkillGrantRequest(false, 1L));
                    return null;
                });
                return runtime.freeze(USER, scopeId);
            });
            assertThat(result.skills()).isEmpty();
        }
    }

    @Test
    void repeatableReadSeesCatalogCommittedAfterSnapshot(@TempDir Path directory) {
        try (ConfigurableApplicationContext context = start(directory)) {
            SkillService skills = context.getBean(SkillService.class);
            SkillRuntimeService runtime = context.getBean(SkillRuntimeService.class);
            JdbcTemplate jdbc = context.getBean(JdbcTemplate.class);
            String version = skills.importPackage(USER, request("1.0.0", "已固定资源")).skillVersionId();
            skills.changeGrant(USER, version, new SkillGrantRequest(true, 0L));
            var message = context.getBean(MessageAcceptanceService.class).accept(USER, null, "rr-catalog",
                    new MessageCreateRequest("并发固定目录", List.of()));
            String scopeId = scope(jdbc, message.executionId());
            TransactionTemplate outer = repeatableRead(context);
            TransactionTemplate independent = independent(context);

            SkillCatalog result = outer.execute(status -> {
                jdbc.queryForObject("SELECT COUNT(*) FROM sq_scope_skill_catalog WHERE user_id = ?", Integer.class, USER);
                independent.execute(inner -> runtime.freeze(USER, scopeId));
                return runtime.freeze(USER, scopeId);
            });
            assertThat(result.skills()).extracting("skillVersionId").containsExactly(version);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sq_scope_skill_catalog WHERE scope_id = ?",
                    Integer.class, scopeId)).isEqualTo(1);
        }
    }

    @Test
    void repeatableReadDoesNotHideNewPinnedRevocation(@TempDir Path directory) {
        try (ConfigurableApplicationContext context = start(directory)) {
            SkillService skills = context.getBean(SkillService.class);
            SkillRuntimeService runtime = context.getBean(SkillRuntimeService.class);
            JdbcTemplate jdbc = context.getBean(JdbcTemplate.class);
            String version = skills.importPackage(USER, request("1.0.0", "禁止回流资源")).skillVersionId();
            skills.changeGrant(USER, version, new SkillGrantRequest(true, 0L));
            var message = context.getBean(MessageAcceptanceService.class).accept(USER, null, "rr-revocation",
                    new MessageCreateRequest("并发安全撤销", List.of()));
            String scopeId = scope(jdbc, message.executionId());
            TransactionTemplate outer = repeatableRead(context);
            TransactionTemplate independent = independent(context);

            assertThatThrownBy(() -> outer.execute(status -> {
                jdbc.queryForObject("SELECT COUNT(*) FROM sq_scope_skill_ref WHERE user_id = ?", Integer.class, USER);
                independent.execute(inner -> runtime.freeze(USER, scopeId));
                independent.execute(inner -> skills.revoke(USER, version, 1L));
                runtime.validate(USER, scopeId);
                return null;
            })).hasMessageContaining("SKILL_REVOKED");
        }
    }

    private TransactionTemplate repeatableRead(ConfigurableApplicationContext context) {
        TransactionTemplate template = new TransactionTemplate(context.getBean(PlatformTransactionManager.class));
        template.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
        return template;
    }

    private TransactionTemplate independent(ConfigurableApplicationContext context) {
        TransactionTemplate template = new TransactionTemplate(context.getBean(PlatformTransactionManager.class));
        template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        return template;
    }

    private SkillImportRequest request(String version, String resource) {
        return new SkillImportRequest(List.of(new SkillFile("SKILL.md", "---\nname: writer\nversion: " + version
                        + "\ndescription: 写作流程\n---\n使用流程：先读取 references/guide.md。"),
                new SkillFile("references/guide.md", resource)));
    }

    private String scope(JdbcTemplate jdbc, String executionId) {
        return jdbc.queryForObject("SELECT scope_id FROM sq_response_execution WHERE execution_id = ?",
                String.class, executionId);
    }

    private ConfigurableApplicationContext start(Path directory) {
        return SpringApplication.run(AgentSquadApplication.class,
                "--server.port=0", "--spring.datasource.url=" + com.agentsquad.squad.testsupport.MySqlTestDatabase.url(directory.resolve("skills")),
                "--spring.datasource.username=" + com.agentsquad.squad.testsupport.MySqlTestDatabase.username(), "--spring.datasource.password=" + com.agentsquad.squad.testsupport.MySqlTestDatabase.password(), "--spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver",
                "--agentsquad.auth.username=local-user", "--agentsquad.auth.password=local-password",
                "--agentsquad.auth.user-id=" + USER, "--agentsquad.delivery.enabled=false");
    }
}
