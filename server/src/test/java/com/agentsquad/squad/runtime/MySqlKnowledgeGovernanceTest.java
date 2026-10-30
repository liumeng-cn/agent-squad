package com.agentsquad.squad.runtime;

import com.agentsquad.squad.AgentSquadApplication;
import com.agentsquad.squad.conversation.dto.MessageCreateRequest;
import com.agentsquad.squad.conversation.service.MessageAcceptanceService;
import com.agentsquad.squad.memory.dto.MemoryCreate;
import com.agentsquad.squad.memory.dto.MemoryUpdate;
import com.agentsquad.squad.memory.service.MemoryService;
import com.agentsquad.squad.runtime.service.ExecutionAuthorizationService;
import com.agentsquad.squad.skill.dto.SkillFile;
import com.agentsquad.squad.skill.dto.SkillGrantRequest;
import com.agentsquad.squad.skill.dto.SkillImportRequest;
import com.agentsquad.squad.skill.service.SkillService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.SpringApplication;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 隔离MySQL上的知识治理迁移与权威校验。
 * <p>
 * 仅接受显式专用测试库变量，不清库，不读取开发环境配置；覆盖真实JSON、唯一键与固定引用失效。
 */
@EnabledIfEnvironmentVariable(named = "SQUAD_D45_TEST_URL", matches = "jdbc:mysql:.*")
class MySqlKnowledgeGovernanceTest {
    @Test
    void migratesAndKeepsUserOwnedPinsAndTombstones() {
        try (var context = SpringApplication.run(AgentSquadApplication.class,
                "--spring.main.web-application-type=none", "--spring.datasource.url=" + System.getenv("SQUAD_D45_TEST_URL"),
                "--spring.datasource.username=" + System.getenv("SQUAD_D45_TEST_USER"),
                "--spring.datasource.password=" + System.getenv("SQUAD_D45_TEST_PASSWORD"),
                "--spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver", "--agentsquad.delivery.enabled=false")) {
            String user = UUID.randomUUID().toString();
            var skills = context.getBean(SkillService.class);
            var imported = skills.importPackage(user, new SkillImportRequest(List.of(new SkillFile("SKILL.md",
                    "---\nname: mysql-rule\ndescription: MySQL验证技能\nversion: 1.0.0\n---\n固定内容"))));
            skills.changeGrant(user, imported.skillVersionId(), new SkillGrantRequest(true, 0L));
            var memory = context.getBean(MemoryService.class).create(user, new MemoryCreate("language", "中文", null));
            var receipt = context.getBean(MessageAcceptanceService.class).accept(user, null, "governance",
                    new MessageCreateRequest("验证隔离知识资料", List.of()));
            var jdbc = context.getBean(JdbcTemplate.class);
            String scope = jdbc.queryForObject("SELECT scope_id FROM sq_response_execution WHERE execution_id=?", String.class, receipt.executionId());
            var gate = context.getBean(ExecutionAuthorizationService.class);
            var fixed = gate.prepare(user, scope);
            assertThat(fixed.skills().skills()).hasSize(1);
            assertThat(fixed.memory().memories()).hasSize(1);
            skills.changeGrant(user, imported.skillVersionId(), new SkillGrantRequest(false, 1L));
            gate.requireScopeCurrent(user, scope);
            assertThatThrownBy(() -> skills.detail(UUID.randomUUID().toString(), imported.skillVersionId())).hasMessageContaining("不存在");
            var memories = context.getBean(MemoryService.class);
            memories.update(user, memory.memory().memoryId(), new MemoryUpdate(0, "英文", null));
            assertThatThrownBy(() -> gate.requireScopeCurrent(user, scope)).hasMessageContaining("旧上下文");
            memories.delete(user, memory.memory().memoryId(), 1);
            assertThat(jdbc.queryForObject("SELECT content FROM sq_memory WHERE memory_id=?", String.class, memory.memory().memoryId())).isEmpty();
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM flyway_schema_history WHERE version='17' AND success=1", Integer.class)).isEqualTo(1);
        }
    }
}
