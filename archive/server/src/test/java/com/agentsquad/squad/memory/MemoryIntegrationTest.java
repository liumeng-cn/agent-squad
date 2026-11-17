package com.agentsquad.squad.memory;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.agentsquad.squad.testsupport.PostgresTestDatabase;
import com.agentsquad.squad.AgentSquadApplication;
import com.agentsquad.squad.exceptions.MemoryContextInvalidatedException;
import com.agentsquad.squad.exceptions.RuntimeBusinessException;
import com.agentsquad.squad.model.bo.MemoryWriteRequestBO;
import com.agentsquad.squad.model.dto.MemoryCreateDTO;
import com.agentsquad.squad.model.dto.MemoryUpdateDTO;
import com.agentsquad.squad.model.dto.MessageCreateDTO;
import com.agentsquad.squad.service.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;

import java.nio.file.Path;
import java.time.ZoneOffset;
import java.util.List;

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
            Long user = IdWorker.getId();
            var created = service.create(user, new MemoryCreateDTO("language", "中文", null));
            Long id = created.memory().memoryId();
            assertThat(service.create(user, new MemoryCreateDTO("language", "中文", null)).memory().memoryId()).isEqualTo(id);
            assertThatThrownBy(() -> service.create(user, new MemoryCreateDTO("language", "English", null))).isInstanceOf(RuntimeBusinessException.class);
            assertThatThrownBy(() -> service.update(IdWorker.getId(), id, new MemoryUpdateDTO(0, "English", null))).isInstanceOf(RuntimeBusinessException.class);
            assertThat(service.list(IdWorker.getId(), null, 20).items()).isEmpty();
            var corrected = service.update(user, id, new MemoryUpdateDTO(0, "简体中文", null));
            assertThat(corrected.memory().version()).isEqualTo(1);
            assertThatThrownBy(() -> service.update(user, id, new MemoryUpdateDTO(0, "旧值", null))).isInstanceOf(RuntimeBusinessException.class);
            assertThat(service.search(user, "简体", 20).getFirst().content()).isEqualTo("简体中文");
            assertThat(service.list(user, null, 20).items().getFirst().content()).isEqualTo("简体中文");
        }
    }

    @Test
    void correctionAndDeletePersistBarrierAcrossRestart(@TempDir Path directory) {
        String database = database(directory);
        Long user = IdWorker.getId();
        Long scope;
        Long id;
        try (var context = start(database)) {
            var service = context.getBean(MemoryService.class);
            id = service.create(user, new MemoryCreateDTO("preference", "旧值", null)).memory().memoryId();
            scope = newScope(context, user, "请回复");
            var contexts = context.getBean(MemoryContextService.class);
            assertThat(contexts.freeze(user, scope).memories().getFirst().content()).isEqualTo("旧值");
            assertThatThrownBy(() -> contexts.select(user, scope, List.of(IdWorker.getId()))).isInstanceOf(RuntimeBusinessException.class);
            var changed = service.update(user, id, new MemoryUpdateDTO(0, "新值", null));
            assertThat(changed.affectedScopes()).isEqualTo(1);
            assertThatThrownBy(() -> contexts.requireCurrent(user, scope)).isInstanceOf(MemoryContextInvalidatedException.class);
            Long next = newScope(context, user, "再回复");
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
            Long fresh = newScope(context, user, "删除后新回复");
            assertThat(context.getBean(MemoryContextService.class).freeze(user, fresh).memories()).isEmpty();
            assertThat(context.getBean(MemoryService.class).search(user, "新值", 20)).isEmpty();
        }
    }

    @Test
    void expiryInvalidatesEvenWithoutRevisionAdvance(@TempDir Path directory) {
        try (var context = start(database(directory))) {
            Long user = IdWorker.getId();
            Long id = context.getBean(MemoryService.class).create(user,
                    new MemoryCreateDTO("expiry", "有时效的内容", java.time.OffsetDateTime.now(ZoneOffset.UTC).plusHours(1))).memory().memoryId();
            Long scope = newScope(context, user, "读取有效内容");
            var contexts = context.getBean(MemoryContextService.class);
            assertThat(contexts.freeze(user, scope).memoryRevision()).isZero();
            context.getBean(JdbcTemplate.class).update("UPDATE sq_memory SET expires_at='2000-01-01' WHERE memory_id=?", id);
            assertThatThrownBy(() -> contexts.requireCurrent(user, scope)).isInstanceOf(MemoryContextInvalidatedException.class);
        }
    }

    @Test
    void derivesSourceFromResponseAndDeduplicatesToolCalls(@TempDir Path directory) {
        try (var context = start(database(directory))) {
            Long user = IdWorker.getId();
            Long scope = newScope(context, user, "请记住：我喜欢中文回复");
            JdbcTemplate jdbc = context.getBean(JdbcTemplate.class);
            String session = jdbc.queryForObject("SELECT framework_session_id FROM sq_runtime_binding WHERE scope_id=?", String.class, scope);
            var service = context.getBean(ExplicitMemoryWriteService.class);
            var request = new MemoryWriteRequestBO("remember", "CREATE", "language", "中文", null, null, "请记住：我喜欢中文回复");
            var receipt = service.write(user, session, request);
            assertThat(service.write(user, session, request)).isEqualTo(receipt);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sq_memory WHERE user_id=?", Long.class, user)).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT source_message_id FROM sq_memory WHERE memory_id=?", Long.class, receipt.memoryId())).isNotNull();
            assertThat(jdbc.queryForObject("SELECT result_json FROM sq_memory_write_receipt WHERE user_id=?", String.class, user)).doesNotContain("中文");
            assertThatThrownBy(() -> service.write(user, session,
                    new MemoryWriteRequestBO("forged", "CREATE", "bad", "bad", null, null, "另一条用户消息")))
                    .isInstanceOf(RuntimeBusinessException.class);
            assertThatThrownBy(() -> service.write(user, session,
                    new MemoryWriteRequestBO("remember", "CREATE", "other", "bad", null, null, "请记住")))
                    .isInstanceOf(RuntimeBusinessException.class);
            GoalTaskService tasks = context.getBean(GoalTaskService.class);
            Long conversation = tasks.createConversation(user, "禁止Worker写记忆").conversationId();
            Long task = tasks.submit(user, conversation, "task", "后台工作").taskId();
            String taskSession = jdbc.queryForObject("SELECT framework_session_id FROM sq_runtime_binding WHERE task_id=?", String.class, task);
            assertThatThrownBy(() -> service.write(user, taskSession, request)).isInstanceOf(RuntimeBusinessException.class);
            Long sourceId = jdbc.queryForObject("SELECT source_message_id FROM sq_memory WHERE memory_id=?", Long.class, receipt.memoryId());
            context.getBean(MemoryService.class).delete(user, receipt.memoryId(), 0);
            assertThat(context.getBean(MemoryContextService.class).excludedHistoryMessageIds(user)).contains(sourceId);
            assertThat(context.getBean(MemoryContextService.class).excludedHistoryMessageIds(IdWorker.getId())).isEmpty();

        }
    }

    @Test
    void concurrentReplayReturnsCommittedReceipt(@TempDir Path directory) throws Exception {
        try (var context = start(database(directory))) {
            Long user = IdWorker.getId();
            Long scope = newScope(context, user, "请记住：优先中文回复");
            JdbcTemplate jdbc = context.getBean(JdbcTemplate.class);
            String session = jdbc.queryForObject("SELECT framework_session_id FROM sq_runtime_binding WHERE scope_id=?", String.class, scope);
            var service = context.getBean(ExplicitMemoryWriteService.class);
            var request = new MemoryWriteRequestBO("rr-replay", "CREATE", "language", "中文", null, null, "请记住：优先中文回复");
            var snapshotReady = new java.util.concurrent.CountDownLatch(1);
            var writerCommitted = new java.util.concurrent.CountDownLatch(1);
            var transactions = new org.springframework.transaction.support.TransactionTemplate(
                    context.getBean(org.springframework.transaction.PlatformTransactionManager.class));
            // PostgreSQL 可在快照后并发插入时抛出序列化失败，重放按读已提交验证后提交回执。
            transactions.setIsolationLevel(org.springframework.transaction.TransactionDefinition.ISOLATION_READ_COMMITTED);
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
            Long user = IdWorker.getId();
            var service = context.getBean(MemoryService.class);
            var contexts = context.getBean(MemoryContextService.class);
            JdbcTemplate jdbc = context.getBean(JdbcTemplate.class);
            Long oldId = service.create(user, new MemoryCreateDTO("回复语言", "旧偏好", null)).memory().memoryId();
            Long oldScope = newScope(context, user, "使用原偏好回复");
            contexts.freeze(user, oldScope);
            service.delete(user, oldId, 0);

            var recreated = service.create(user, new MemoryCreateDTO("回复语言", "新偏好", null));
            Long newId = recreated.memory().memoryId();
            assertThat(newId).isNotEqualTo(oldId);
            assertThat(recreated.memory().version()).isZero();
            assertThat(service.requireOwned(user, oldId).getStatus()).isEqualTo("DELETED");
            assertThat(service.requireOwned(user, oldId).getContent()).isEmpty();
            assertThat(service.create(user, new MemoryCreateDTO("回复语言", "新偏好", null)).memory().memoryId()).isEqualTo(newId);
            assertThatThrownBy(() -> service.create(user, new MemoryCreateDTO("回复语言", "覆盖新偏好", null)))
                    .isInstanceOf(RuntimeBusinessException.class);
            assertThatThrownBy(() -> service.update(user, oldId, new MemoryUpdateDTO(1, "不能复活", null)))
                    .isInstanceOf(RuntimeBusinessException.class);
            assertThatThrownBy(() -> contexts.requireCurrent(user, oldScope)).isInstanceOf(MemoryContextInvalidatedException.class);
            Long fresh = newScope(context, user, "使用新偏好回复");
            assertThat(contexts.freeze(user, fresh).memories()).extracting(memory -> memory.memoryId()).containsExactly(newId);
            assertThat(service.search(user, "回复语言", 20)).extracting(memory -> memory.memoryId()).containsExactly(newId);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sq_memory WHERE user_id=? AND memory_key=?", Long.class, user, "回复语言"))
                    .isEqualTo(2);
            assertThat(jdbc.queryForObject("SELECT active_key FROM sq_memory WHERE memory_id=?", String.class, oldId)).isNull();
            assertThat(jdbc.queryForObject("SELECT active_key FROM sq_memory WHERE memory_id=?", String.class, newId)).isEqualTo("回复语言");

            Long writeScope = newScope(context, user, "把回复语言改成英文");
            String session = jdbc.queryForObject("SELECT framework_session_id FROM sq_runtime_binding WHERE scope_id=?", String.class, writeScope);
            var receipt = context.getBean(ExplicitMemoryWriteService.class).write(user, session,
                    new MemoryWriteRequestBO("correct-current", "UPDATE", "回复语言", "英文", 0L, null, "把回复语言改成英文"));
            assertThat(receipt.memoryId()).isEqualTo(newId);
            assertThat(service.requireOwned(user, oldId).getStatus()).isEqualTo("DELETED");
            assertThat(service.requireOwned(user, newId).getContent()).isEqualTo("英文");
            service.delete(user, newId, 1);
            Long thirdId = service.create(user, new MemoryCreateDTO("回复语言", "第三次偏好", null)).memory().memoryId();
            assertThat(thirdId).isNotIn(oldId, newId);
        }
    }

    @Test
    void upgradesV17TombstonesWithoutChangingTheirIdentity(@TempDir Path directory) throws Exception {
        String url = database(directory);
        var dataSource = new org.springframework.jdbc.datasource.DriverManagerDataSource(url,
                PostgresTestDatabase.username(),
                PostgresTestDatabase.password());
        JdbcTemplate beforeUpgrade = new JdbcTemplate(dataSource);
        Long user = IdWorker.getId();
        Long oldId = IdWorker.getId();
        beforeUpgrade.update("INSERT INTO sq_memory (memory_id,user_id,memory_key,content,status,version) VALUES (?,?,?,'','DELETED',1)",
                oldId, user, "可重新使用的键");
        try (var context = start(url)) {
            MemoryService service = context.getBean(MemoryService.class);
            Long newId = service.create(user, new MemoryCreateDTO("可重新使用的键", "升级后明确重新创建", null)).memory().memoryId();
            assertThat(newId).isNotEqualTo(oldId);
            assertThat(service.requireOwned(user, oldId).getStatus()).isEqualTo("DELETED");
            assertThat(service.requireOwned(user, oldId).getVersion()).isEqualTo(1);
            assertThat(service.requireOwned(user, oldId).getContent()).isEmpty();
            assertThat(service.search(user, "可重新使用的键", 20)).extracting(memory -> memory.memoryId()).containsExactly(newId);
        }
    }

    private Long newScope(ConfigurableApplicationContext context, Long user, String text) {
        var receipt = context.getBean(MessageAcceptanceService.class).accept(user, null, IdWorker.getIdStr(), new MessageCreateDTO(text, List.of()));
        return context.getBean(ExecutionScopeService.class).scopeForSession(user,
                context.getBean(JdbcTemplate.class).queryForObject("SELECT framework_session_id FROM sq_runtime_binding WHERE binding_id=(SELECT binding_id FROM sq_response_execution WHERE execution_id=?)", String.class, receipt.executionId()));
    }

    private String database(Path directory) {
        return PostgresTestDatabase.url(directory.resolve("memory"));
    }

    private ConfigurableApplicationContext start(String database) {
        return SpringApplication.run(AgentSquadApplication.class, "--server.port=0",
                "--spring.datasource.url=" + database,
                "--spring.datasource.username=" + PostgresTestDatabase.username(), "--spring.datasource.password=" + PostgresTestDatabase.password(), "--spring.datasource.driver-class-name=org.postgresql.Driver",
                "--agentsquad.delivery.enabled=false", "--agentsquad.auth.username=tester", "--agentsquad.auth.password=test-only",
                "--agentsquad.auth.role=USER", "--agentsquad.auth.user-id=" + IdWorker.getId());
    }
}
