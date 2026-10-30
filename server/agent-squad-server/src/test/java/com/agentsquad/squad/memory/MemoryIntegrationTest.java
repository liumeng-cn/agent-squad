package com.agentsquad.squad.memory;

import com.agentsquad.squad.AgentSquadApplication;
import com.agentsquad.squad.common.exception.RuntimeBusinessException;
import com.agentsquad.squad.conversation.dto.MessageCreateRequest;
import com.agentsquad.squad.conversation.service.MessageAcceptanceService;
import com.agentsquad.squad.conversation.service.ExecutionScopeService;
import com.agentsquad.squad.conversation.service.GoalTaskService;
import com.agentsquad.squad.memory.domain.MemoryContextInvalidatedException;
import com.agentsquad.squad.memory.dto.MemoryCreate;
import com.agentsquad.squad.memory.dto.MemoryUpdate;
import com.agentsquad.squad.memory.dto.MemoryWriteRequest;
import com.agentsquad.squad.memory.service.ExplicitMemoryWriteService;
import com.agentsquad.squad.memory.service.MemoryContextService;
import com.agentsquad.squad.memory.service.MemoryService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 显式记忆所有权、幂等和持久删除屏障的正式服务集成验证。
 * <p>
 * 使用本地独立MySQL测试库与正式迁移，不调用模型；原生Session是否被阻断由共享运行集成测试补证。
 */
class MemoryIntegrationTest {
    @Test
    void ownsRecordsAndUsesCasWithoutOverwritingNewValues(@TempDir Path directory) {
        try (var context = start(database(directory))) {
            MemoryService service = context.getBean(MemoryService.class);
            String user = UUID.randomUUID().toString();
            var created = service.create(user, new MemoryCreate("language", "中文", null));
            String id = created.memory().memoryId();
            assertThat(service.create(user, new MemoryCreate("language", "中文", null)).memory().memoryId()).isEqualTo(id);
            assertThatThrownBy(() -> service.create(user, new MemoryCreate("language", "English", null))).isInstanceOf(RuntimeBusinessException.class);
            assertThatThrownBy(() -> service.update("foreign", id, new MemoryUpdate(0, "English", null))).isInstanceOf(RuntimeBusinessException.class);
            assertThat(service.list("foreign", null, 20).items()).isEmpty();
            var corrected = service.update(user, id, new MemoryUpdate(0, "简体中文", null));
            assertThat(corrected.memory().version()).isEqualTo(1);
            assertThatThrownBy(() -> service.update(user, id, new MemoryUpdate(0, "旧值", null))).isInstanceOf(RuntimeBusinessException.class);
            assertThat(service.search(user, "简体", 20).getFirst().content()).isEqualTo("简体中文");
            assertThat(service.list(user, null, 20).items().getFirst().content()).isEqualTo("简体中文");
        }
    }

    @Test
    void correctionAndDeletePersistBarrierAcrossRestart(@TempDir Path directory) {
        String database = database(directory);
        String user = UUID.randomUUID().toString();
        String scope;
        String id;
        try (var context = start(database)) {
            var service = context.getBean(MemoryService.class);
            id = service.create(user, new MemoryCreate("preference", "旧值", null)).memory().memoryId();
            scope = newScope(context, user, "请回复");
            var contexts = context.getBean(MemoryContextService.class);
            assertThat(contexts.freeze(user, scope).memories().getFirst().content()).isEqualTo("旧值");
            assertThatThrownBy(() -> contexts.select(user, scope, List.of(UUID.randomUUID().toString()))).isInstanceOf(RuntimeBusinessException.class);
            var changed = service.update(user, id, new MemoryUpdate(0, "新值", null));
            assertThat(changed.affectedScopes()).isEqualTo(1);
            assertThatThrownBy(() -> contexts.requireCurrent(user, scope)).isInstanceOf(MemoryContextInvalidatedException.class);
            String next = newScope(context, user, "再回复");
            assertThat(contexts.freeze(user, next).memories().getFirst().content()).isEqualTo("新值");
            var deleted = service.delete(user, id, 1);
            assertThat(deleted.memory().content()).isEmpty();
            assertThat(service.delete(user, id, 1).memory().version()).isEqualTo(2);
            assertThatThrownBy(() -> contexts.read(user, next)).isInstanceOf(MemoryContextInvalidatedException.class);
            assertThat(context.getBean(JdbcTemplate.class).queryForObject("SELECT content FROM sq_memory WHERE memory_id=?", String.class, id)).isEmpty();
        }
        try (var context = start(database)) {
            assertThatThrownBy(() -> context.getBean(MemoryContextService.class).requireCurrent(user, scope))
                    .isInstanceOf(MemoryContextInvalidatedException.class);
            String fresh = newScope(context, user, "删除后新回复");
            assertThat(context.getBean(MemoryContextService.class).freeze(user, fresh).memories()).isEmpty();
            assertThat(context.getBean(MemoryService.class).search(user, "新值", 20)).isEmpty();
        }
    }

    @Test
    void expiryInvalidatesEvenWithoutRevisionAdvance(@TempDir Path directory) {
        try (var context = start(database(directory))) {
            String user = UUID.randomUUID().toString();
            String id = context.getBean(MemoryService.class).create(user,
                    new MemoryCreate("expiry", "有时效的内容", java.time.OffsetDateTime.now(ZoneOffset.UTC).plusHours(1))).memory().memoryId();
            String scope = newScope(context, user, "读取有效内容");
            var contexts = context.getBean(MemoryContextService.class);
            assertThat(contexts.freeze(user, scope).memoryRevision()).isZero();
            context.getBean(JdbcTemplate.class).update("UPDATE sq_memory SET expires_at='2000-01-01' WHERE memory_id=?", id);
            assertThatThrownBy(() -> contexts.requireCurrent(user, scope)).isInstanceOf(MemoryContextInvalidatedException.class);
        }
    }

    @Test
    void derivesSourceFromResponseAndDeduplicatesToolCalls(@TempDir Path directory) {
        try (var context = start(database(directory))) {
            String user = UUID.randomUUID().toString();
            String scope = newScope(context, user, "请记住：我喜欢中文回复");
            JdbcTemplate jdbc = context.getBean(JdbcTemplate.class);
            String session = jdbc.queryForObject("SELECT framework_session_id FROM sq_runtime_binding WHERE scope_id=?", String.class, scope);
            var service = context.getBean(ExplicitMemoryWriteService.class);
            var request = new MemoryWriteRequest("remember", "CREATE", "language", "中文", null, null, "请记住：我喜欢中文回复");
            var receipt = service.write(user, session, request);
            assertThat(service.write(user, session, request)).isEqualTo(receipt);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sq_memory WHERE user_id=?", Long.class, user)).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT source_message_id FROM sq_memory WHERE memory_id=?", String.class, receipt.memoryId())).isNotBlank();
            assertThat(jdbc.queryForObject("SELECT result_json FROM sq_memory_write_receipt WHERE user_id=?", String.class, user)).doesNotContain("中文");
            assertThatThrownBy(() -> service.write(user, session,
                    new MemoryWriteRequest("forged", "CREATE", "bad", "bad", null, null, "另一条用户消息")))
                    .isInstanceOf(RuntimeBusinessException.class);
            assertThatThrownBy(() -> service.write(user, session,
                    new MemoryWriteRequest("remember", "CREATE", "other", "bad", null, null, "请记住")))
                    .isInstanceOf(RuntimeBusinessException.class);
            GoalTaskService tasks = context.getBean(GoalTaskService.class);
            String conversation = tasks.createConversation(user, "禁止Worker写记忆").conversationId();
            String task = tasks.submit(user, conversation, "task", "后台工作").taskId();
            String taskSession = jdbc.queryForObject("SELECT framework_session_id FROM sq_runtime_binding WHERE task_id=?", String.class, task);
            assertThatThrownBy(() -> service.write(user, taskSession, request)).isInstanceOf(RuntimeBusinessException.class);
            String sourceId = jdbc.queryForObject("SELECT source_message_id FROM sq_memory WHERE memory_id=?", String.class, receipt.memoryId());
            context.getBean(MemoryService.class).delete(user, receipt.memoryId(), 0);
            assertThat(context.getBean(MemoryContextService.class).excludedHistoryMessageIds(user)).contains(sourceId);
            assertThat(context.getBean(MemoryContextService.class).excludedHistoryMessageIds("foreign")).isEmpty();

        }
    }

    @Test
    void replayUsesCurrentReceiptAfterAnOlderRepeatableReadSnapshot(@TempDir Path directory) throws Exception {
        try (var context = start(database(directory))) {
            String user = UUID.randomUUID().toString();
            String scope = newScope(context, user, "请记住：优先中文回复");
            JdbcTemplate jdbc = context.getBean(JdbcTemplate.class);
            String session = jdbc.queryForObject("SELECT framework_session_id FROM sq_runtime_binding WHERE scope_id=?", String.class, scope);
            var service = context.getBean(ExplicitMemoryWriteService.class);
            var request = new MemoryWriteRequest("rr-replay", "CREATE", "language", "中文", null, null, "请记住：优先中文回复");
            var snapshotReady = new java.util.concurrent.CountDownLatch(1);
            var writerCommitted = new java.util.concurrent.CountDownLatch(1);
            var transactions = new org.springframework.transaction.support.TransactionTemplate(
                    context.getBean(org.springframework.transaction.PlatformTransactionManager.class));
            transactions.setIsolationLevel(org.springframework.transaction.TransactionDefinition.ISOLATION_REPEATABLE_READ);
            try (var executor = java.util.concurrent.Executors.newSingleThreadExecutor()) {
                var replay = executor.submit(() -> transactions.execute(status -> {
                    jdbc.queryForObject("SELECT COUNT(*) FROM sq_memory_write_receipt WHERE user_id=?", Long.class, user);
                    snapshotReady.countDown();
                    try {
                        if (!writerCommitted.await(10, java.util.concurrent.TimeUnit.SECONDS)) {
                            throw new IllegalStateException("等待记忆首次写入超时");
                        }
                    } catch (InterruptedException exception) {
                        Thread.currentThread().interrupt();
                        throw new IllegalStateException("记忆回执重放被中断", exception);
                    }
                    return service.write(user, session, request);
                }));
                assertThat(snapshotReady.await(10, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
                var original = service.write(user, session, request);
                writerCommitted.countDown();
                assertThat(replay.get(10, java.util.concurrent.TimeUnit.SECONDS)).isEqualTo(original);
                assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sq_memory_write_receipt WHERE user_id=?", Long.class, user)).isEqualTo(1);
                assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sq_memory WHERE user_id=?", Long.class, user)).isEqualTo(1);
            } finally {
                writerCommitted.countDown();
            }
        }
    }

    @Test
    void recreatesDeletedKeyWithNewIdentityWithoutRevivingOldReferences(@TempDir Path directory) {
        try (var context = start(database(directory))) {
            String user = UUID.randomUUID().toString();
            var service = context.getBean(MemoryService.class);
            var contexts = context.getBean(MemoryContextService.class);
            JdbcTemplate jdbc = context.getBean(JdbcTemplate.class);
            String oldId = service.create(user, new MemoryCreate("回复语言", "旧偏好", null)).memory().memoryId();
            String oldScope = newScope(context, user, "使用原偏好回复");
            contexts.freeze(user, oldScope);
            service.delete(user, oldId, 0);

            var recreated = service.create(user, new MemoryCreate("回复语言", "新偏好", null));
            String newId = recreated.memory().memoryId();
            assertThat(newId).isNotEqualTo(oldId);
            assertThat(recreated.memory().version()).isZero();
            assertThat(service.requireOwned(user, oldId).status()).isEqualTo("DELETED");
            assertThat(service.requireOwned(user, oldId).content()).isEmpty();
            assertThat(service.create(user, new MemoryCreate("回复语言", "新偏好", null)).memory().memoryId()).isEqualTo(newId);
            assertThatThrownBy(() -> service.create(user, new MemoryCreate("回复语言", "覆盖新偏好", null)))
                    .isInstanceOf(RuntimeBusinessException.class);
            assertThatThrownBy(() -> service.update(user, oldId, new MemoryUpdate(1, "不能复活", null)))
                    .isInstanceOf(RuntimeBusinessException.class);
            assertThatThrownBy(() -> contexts.requireCurrent(user, oldScope)).isInstanceOf(MemoryContextInvalidatedException.class);
            String fresh = newScope(context, user, "使用新偏好回复");
            assertThat(contexts.freeze(user, fresh).memories()).extracting(memory -> memory.memoryId()).containsExactly(newId);
            assertThat(service.search(user, "回复语言", 20)).extracting(memory -> memory.memoryId()).containsExactly(newId);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sq_memory WHERE user_id=? AND memory_key=?", Long.class, user, "回复语言"))
                    .isEqualTo(2);
            assertThat(jdbc.queryForObject("SELECT active_key FROM sq_memory WHERE memory_id=?", String.class, oldId)).isNull();
            assertThat(jdbc.queryForObject("SELECT active_key FROM sq_memory WHERE memory_id=?", String.class, newId)).isEqualTo("回复语言");

            String writeScope = newScope(context, user, "把回复语言改成英文");
            String session = jdbc.queryForObject("SELECT framework_session_id FROM sq_runtime_binding WHERE scope_id=?", String.class, writeScope);
            var receipt = context.getBean(ExplicitMemoryWriteService.class).write(user, session,
                    new MemoryWriteRequest("correct-current", "UPDATE", "回复语言", "英文", 0L, null, "把回复语言改成英文"));
            assertThat(receipt.memoryId()).isEqualTo(newId);
            assertThat(service.requireOwned(user, oldId).status()).isEqualTo("DELETED");
            assertThat(service.requireOwned(user, newId).content()).isEqualTo("英文");
            service.delete(user, newId, 1);
            String thirdId = service.create(user, new MemoryCreate("回复语言", "第三次偏好", null)).memory().memoryId();
            assertThat(thirdId).isNotIn(oldId, newId);
        }
    }

    @Test
    void upgradesV17TombstonesWithoutChangingTheirIdentity(@TempDir Path directory) {
        String url = database(directory);
        org.flywaydb.core.Flyway.configure().locations("classpath:db/legacy-migration", "classpath:legacy/migration").dataSource(url,
                        com.agentsquad.squad.testsupport.MySqlTestDatabase.username(),
                        com.agentsquad.squad.testsupport.MySqlTestDatabase.password())
                .target("17").load().migrate();
        var dataSource = new org.springframework.jdbc.datasource.DriverManagerDataSource(url,
                com.agentsquad.squad.testsupport.MySqlTestDatabase.username(),
                com.agentsquad.squad.testsupport.MySqlTestDatabase.password());
        JdbcTemplate beforeUpgrade = new JdbcTemplate(dataSource);
        String user = UUID.randomUUID().toString();
        String oldId = UUID.randomUUID().toString();
        beforeUpgrade.update("INSERT INTO sq_memory (memory_id,user_id,memory_key,content,status,version) VALUES (?,?,?,'','DELETED',1)",
                oldId, user, "可重新使用的键");
        try (var context = start(url, "classpath:db/legacy-migration,classpath:legacy/migration")) {
            MemoryService service = context.getBean(MemoryService.class);
            String newId = service.create(user, new MemoryCreate("可重新使用的键", "升级后明确重新创建", null)).memory().memoryId();
            assertThat(newId).isNotEqualTo(oldId);
            assertThat(service.requireOwned(user, oldId).status()).isEqualTo("DELETED");
            assertThat(service.requireOwned(user, oldId).version()).isEqualTo(1);
            assertThat(service.requireOwned(user, oldId).content()).isEmpty();
            assertThat(service.search(user, "可重新使用的键", 20)).extracting(memory -> memory.memoryId()).containsExactly(newId);
        }
    }

    private String newScope(ConfigurableApplicationContext context, String user, String text) {
        var receipt = context.getBean(MessageAcceptanceService.class).accept(user, null, UUID.randomUUID().toString(), new MessageCreateRequest(text, List.of()));
        return context.getBean(ExecutionScopeService.class).scopeForSession(user,
                context.getBean(JdbcTemplate.class).queryForObject("SELECT framework_session_id FROM sq_runtime_binding WHERE binding_id=(SELECT binding_id FROM sq_response_execution WHERE execution_id=?)", String.class, receipt.executionId()));
    }

    private String database(Path directory) {
        return com.agentsquad.squad.testsupport.MySqlTestDatabase.url(directory.resolve("memory"));
    }

    private ConfigurableApplicationContext start(String database) {
        return start(database, "classpath:db/migration");
    }

    private ConfigurableApplicationContext start(String database, String locations) {
        return SpringApplication.run(AgentSquadApplication.class, "--server.port=0", "--spring.flyway.locations=" + locations, "--spring.datasource.url=" + database,
                "--spring.datasource.username=" + com.agentsquad.squad.testsupport.MySqlTestDatabase.username(), "--spring.datasource.password=" + com.agentsquad.squad.testsupport.MySqlTestDatabase.password(), "--spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver",
                "--agentsquad.delivery.enabled=false", "--agentsquad.auth.username=tester", "--agentsquad.auth.password=test-only",
                "--agentsquad.auth.role=USER", "--agentsquad.auth.user-id=" + UUID.randomUUID());
    }
}
