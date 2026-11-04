package com.agentsquad.experiments;

import com.agentsquad.squad.integration.agentscope.inbox.RuntimeEventInboxMapper;
import com.agentsquad.squad.integration.agentscope.inbox.RuntimeEventReceiptService;
import com.agentsquad.squad.integration.agentscope.inbox.RuntimeEventSource;
import com.agentsquad.squad.integration.agentscope.inbox.RuntimeEventConsumptionService;
import com.agentsquad.squad.integration.agentscope.inbox.RuntimeEventBusinessWriter;
import com.agentsquad.squad.integration.agentscope.inbox.RuntimeEventFailureService;
import com.agentsquad.squad.integration.agentscope.inbox.RuntimeEventFailureCode;
import com.agentsquad.squad.integration.agentscope.RuntimeEventConsumptionException;
import com.agentsquad.squad.runtime.domain.ProjectionTarget;
import com.agentsquad.squad.integration.agentscope.journal.RuntimeJournalStore;
import com.agentsquad.squad.integration.agentscope.journal.RuntimeSessionProvider;
import com.agentsquad.squad.integration.agentscope.inbox.RuntimeEventSink;
import com.agentsquad.squad.integration.agentscope.inbox.RuntimeEventSinkFactory;
import com.mysql.cj.jdbc.MysqlDataSource;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.session.InMemorySessionLogStore;
import io.agentscope.core.session.SessionEvent;
import io.agentscope.core.session.SessionKey;
import io.agentscope.core.session.SessionLogExporter;
import io.agentscope.core.session.SessionExportSink;
import io.agentscope.core.tool.Toolkit;
import io.agentscope.extensions.jdbc.JdbcDistributedStore;
import io.agentscope.extensions.jdbc.dialect.AbstractJdbcDialect;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.core.io.FileSystemResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.spy;

/**
 * 生产收件服务的隔离MySQL事务验证
 * <p>
 * 直接装配生产Mapper XML和Spring事务代理，不启动应用或调度器；所有DDL与测试数据仅存在于专用容器。
 */
@EnabledIfEnvironmentVariable(named = "SQUAD_PROBE_MYSQL_URL", matches = ".*/squad_sdk_probe\\?.*")
class RuntimeEventReceiptMySqlTest {
    private static final Path REPO = findRepository();
    private static final Path RESOURCES = REPO.resolve("server/agent-squad-server/src/main/resources");
    private static MysqlDataSource source;
    private static JdbcTemplate jdbc;
    private static RuntimeEventInboxMapper mapper;
    private static RuntimeEventReceiptService receipts;

    @BeforeAll
    static void initialize() throws Exception {
        source = new MySqlProbeStore(false).source();
        jdbc = new JdbcTemplate(source);
        try (var connection = source.getConnection()) {
            ScriptUtils.executeSqlScript(connection, new FileSystemResource(RESOURCES.resolve("db/migration/V1__baseline.sql")));
        }
        var factory = new SqlSessionFactoryBean();
        factory.setDataSource(source);
        factory.setMapperLocations(new FileSystemResource(RESOURCES.resolve("mapper/integration/RuntimeEventInboxMapper.xml")));
        mapper = new SqlSessionTemplate(factory.getObject()).getMapper(RuntimeEventInboxMapper.class);
        receipts = transactionalService(mapper);
        jdbc.execute("CREATE TABLE IF NOT EXISTS probe_projection_effect(binding_id VARCHAR(36), native_seq BIGINT, PRIMARY KEY(binding_id,native_seq))");
    }

    @Test
    void failedBatchKeepsRetryRecordAndSuccessDoesNotRegress() {
        var owner = binding();
        var events = List.of(known(1), known(2));
        events.forEach(event -> sink(receipts, owner).accept(event));
        assertThatThrownBy(() -> consumer(projectionWriter(2)).consume(target(owner), events))
                .isInstanceOf(IllegalStateException.class);
        var failures = failureRecorder();
        failures.record(owner, 2, RuntimeEventFailureCode.BUSINESS_RETRY);
        var retry = jdbc.queryForMap("SELECT status,error_code,attempts,next_attempt_at FROM sq_runtime_event_inbox WHERE binding_id=? AND native_seq=2",
                owner.bindingId());
        assertThat(retry.get("status")).isEqualTo("RETRY");
        assertThat(retry.get("error_code")).isEqualTo("BUSINESS_RETRY");
        assertThat(((Number) retry.get("attempts")).intValue()).isEqualTo(1);
        assertThat(retry.get("next_attempt_at")).isNotNull();
        assertProjection(owner, 0, 0, 0);
        consumer(projectionWriter(0)).consume(target(owner), events);
        failures.record(owner, 2, RuntimeEventFailureCode.BUSINESS_RETRY);
        assertProjection(owner, 2, 2, 2);
        assertThat(jdbc.queryForObject("SELECT error_code FROM sq_runtime_event_inbox WHERE binding_id=? AND native_seq=2",
                String.class, owner.bindingId())).isNull();
        assertThat(jdbc.queryForObject("SELECT attempts FROM sq_runtime_event_inbox WHERE binding_id=? AND native_seq=2",
                Integer.class, owner.bindingId())).isEqualTo(1);
    }

    @Test
    void unknownRequiredEventKeepsSafeBlockedReason() {
        var owner = binding();
        var unknown = event(1, "unsupported", "{\"private\":\"NOT_FOR_ERROR_LOG\"}");
        sink(receipts, owner).accept(unknown);
        try {
            consumer(projectionWriter(0)).consume(target(owner), List.of(unknown));
            throw new AssertionError("未知必需事件不应消费成功");
        } catch (RuntimeEventConsumptionException failure) {
            assertThat(failure.code()).isEqualTo(RuntimeEventFailureCode.UNSUPPORTED_REQUIRED);
            assertThat(failure.getMessage()).doesNotContain("NOT_FOR_ERROR_LOG");
            failureRecorder().record(owner, failure.sequence(), failure.code());
        }
        var row = jdbc.queryForMap("SELECT status,error_code,attempts FROM sq_runtime_event_inbox WHERE binding_id=?", owner.bindingId());
        assertThat(row.get("status")).isEqualTo("BLOCKED");
        assertThat(row.get("error_code")).isEqualTo("UNSUPPORTED_REQUIRED");
        assertProjection(owner, 0, 0, 0);
    }

    private RuntimeEventFailureService failureRecorder() {
        var service = new RuntimeEventFailureService();
        ReflectionTestUtils.setField(service, "inboxMapper", mapper);
        var proxy = new ProxyFactory(service);
        proxy.addAdvice(new TransactionInterceptor(new DataSourceTransactionManager(source),
                new AnnotationTransactionAttributeSource()));
        return (RuntimeEventFailureService) proxy.getProxy();
    }

    @Test
    void consumptionCommitsCursorEffectsAndAcknowledgementTogether() {
        var owner = binding();
        var events = List.of(known(1), known(2));
        events.forEach(event -> sink(receipts, owner).accept(event));
        var consumer = consumer(projectionWriter(0));
        consumer.consume(target(owner), events);
        consumer.consume(target(owner), events);
        assertProjection(owner, 2, 2, 2);
    }

    @Test
    void consumptionFailureRollsBackWholeBatchButKeepsReceipts() {
        var owner = binding();
        var events = List.of(known(1), known(2));
        events.forEach(event -> sink(receipts, owner).accept(event));
        assertThatThrownBy(() -> consumer(projectionWriter(2)).consume(target(owner), events))
                .isInstanceOf(IllegalStateException.class).hasMessage("投影故障注入");
        assertThat(count(owner)).isEqualTo(2);
        assertProjection(owner, 0, 0, 0);
        consumer(projectionWriter(0)).consume(target(owner), events);
        assertProjection(owner, 2, 2, 2);
    }

    @Test
    void consumptionRejectsGapsConflictsAndUnreceivedEvents() {
        var owner = binding();
        var consumer = consumer(projectionWriter(0));
        sink(receipts, owner).accept(known(2));
        assertThatThrownBy(() -> consumer.consume(target(owner), List.of(known(2))))
                .isInstanceOf(IllegalStateException.class).hasMessage("投影序号缺失");
        assertThatThrownBy(() -> consumer.consume(target(owner), List.of(known(1))))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("不一致");
        sink(receipts, owner).accept(known(1));
        var changed = new SessionEvent(1, "known-1", 1, 1, "presentation/hint", "run", "turn", true, "{\"changed\":true}");
        assertThatThrownBy(() -> consumer.consume(target(owner), List.of(changed)))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("不一致");
        assertProjection(owner, 0, 0, 0);
        consumer.consume(target(owner), List.of(known(1), known(2)));
        assertProjection(owner, 2, 2, 2);
    }

    @Test
    void requiredUnknownRollsBackItsBindingWithoutBlockingAnother() {
        var blocked = binding();
        var good = binding();
        var poison = event(2, "unknown-required", "{}");
        sink(receipts, blocked).accept(known(1));
        sink(receipts, blocked).accept(poison);
        var consumer = consumer(projectionWriter(0));
        assertThatThrownBy(() -> consumer.consume(target(blocked), List.of(known(1), poison)))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("尚不支持");
        assertProjection(blocked, 0, 0, 0);
        sink(receipts, good).accept(known(1));
        consumer.consume(target(good), List.of(known(1)));
        assertProjection(good, 1, 1, 1);
    }

    @Test
    void concurrentConsumersDoNotDuplicateBusinessEffects() throws Exception {
        var owner = binding();
        var events = List.of(known(1), known(2));
        events.forEach(event -> sink(receipts, owner).accept(event));
        var ready = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var work = List.of(consumer(projectionWriter(0)), consumer(projectionWriter(0))).stream()
                    .map(service -> executor.submit(() -> {
                        if (!ready.await(5, TimeUnit.SECONDS)) {
                            throw new IllegalStateException("实验门闩超时");
                        }
                        service.consume(target(owner), events);
                        return true;
                    })).toList();
            ready.countDown();
            for (var future : work) {
                assertThat(future.get(10, TimeUnit.SECONDS)).isTrue();
            }
        }
        assertProjection(owner, 2, 2, 2);
    }

    private RuntimeEventConsumptionService consumer(RuntimeEventBusinessWriter writer) {
        var service = new RuntimeEventConsumptionService();
        ReflectionTestUtils.setField(service, "inboxMapper", mapper);
        ReflectionTestUtils.setField(service, "businessWriter", writer);
        var proxy = new ProxyFactory(service);
        proxy.addAdvice(new TransactionInterceptor(new DataSourceTransactionManager(source),
                new AnnotationTransactionAttributeSource()));
        return (RuntimeEventConsumptionService) proxy.getProxy();
    }

    private RuntimeEventBusinessWriter projectionWriter(long failAt) {
        // 夹具只提供事务内的效果与游标写入，生产业务规则仍由NativeProjectionService负责。
        return (target, event) -> {
            long cursor = jdbc.queryForObject("SELECT native_event_cursor FROM sq_runtime_binding WHERE user_id=? AND binding_id=? FOR UPDATE",
                    Long.class, target.userId(), target.bindingId());
            if (event.seq() <= cursor) {
                return;
            }
            if (event.seq() != cursor + 1) {
                throw new IllegalStateException("投影序号缺失");
            }
            jdbc.update("INSERT INTO probe_projection_effect(binding_id,native_seq) VALUES(?,?)", target.bindingId(), event.seq());
            jdbc.update("UPDATE sq_runtime_binding SET native_event_cursor=? WHERE binding_id=? AND user_id=?",
                    event.seq(), target.bindingId(), target.userId());
            if (event.seq() == failAt) {
                throw new IllegalStateException("投影故障注入");
            }
        };
    }

    private ProjectionTarget target(RuntimeEventSource owner) {
        return new ProjectionTarget(owner.userId(), null, owner.bindingId(), "probe", owner.sessionId(), 0);
    }

    private SessionEvent known(long sequence) {
        return new SessionEvent(1, "known-" + sequence, sequence, 1, "presentation/hint", "run", "turn", true, "{}");
    }

    private void assertProjection(RuntimeEventSource owner, long cursor, long effects, long applied) {
        assertThat(jdbc.queryForObject("SELECT native_event_cursor FROM sq_runtime_binding WHERE binding_id=?", Long.class,
                owner.bindingId())).isEqualTo(cursor);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM probe_projection_effect WHERE binding_id=?", Long.class,
                owner.bindingId())).isEqualTo(effects);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sq_runtime_event_inbox WHERE binding_id=? AND status='APPLIED'", Long.class,
                owner.bindingId())).isEqualTo(applied);
    }

    @Test
    void providerCarriesSinkThroughRealHarnessAndReadsWithoutExecution(@TempDir Path directory) throws Exception {
        var owner = binding();
        jdbc.update("UPDATE sq_runtime_binding SET agent_id='probe-agent' WHERE binding_id=?", owner.bindingId());
        var factory = new RuntimeEventSinkFactory();
        ReflectionTestUtils.setField(factory, "receipts", receipts);
        var provider = new RuntimeSessionProvider();
        ReflectionTestUtils.setField(provider, "dataSource", source);
        ReflectionTestUtils.setField(provider, "inboxMapper", mapper);
        ReflectionTestUtils.setField(provider, "sinkFactory", factory);
        provider.initialize();
        assertThat(provider.readCommitted(owner, "probe-agent", 0, 1000)).isEmpty();
        var context = provider.executionContext(owner);
        Object copiedSink = RuntimeContext.builder(context).build().get(SessionExportSink.CONTEXT_KEY);
        assertThat(copiedSink).isNotNull();
        try (var model = new ScriptedModelServer(List.of(ScriptedModelServer.text("stop")), false);
             var agent = SdkExecutionContractTest.agent(model, directory, new Toolkit(), 3, false, provider.logStore());
             var session = agent.session(context)) {
            var receipt = session.submit("provider-probe", "本地Provider实验");
            assertThat(session.await(receipt).block(Duration.ofSeconds(15)).status()).isEqualTo("completed");
            assertThat(count(owner)).isPositive();
            // await确认运行静止，不承诺异步导出已追上；按设计独立补齐尾部，不再调用模型。
            new SessionLogExporter(session.log(), (SessionExportSink) copiedSink).drain();
            var events = provider.readCommitted(owner, "probe-agent", 0, 1000);
            assertThat(events).anyMatch(event -> event.type().equals("turn/output"));
            assertThat(count(owner)).isEqualTo(events.size());
            assertThat(provider.readCommitted(owner, "probe-agent", events.getLast().seq(), 1000)).isEmpty();
            assertThat(model.calls()).isEqualTo(1);
            assertThatThrownBy(() -> provider.readCommitted(owner, "other-agent", 0, 10))
                    .isInstanceOf(IllegalStateException.class);
            assertThatThrownBy(() -> provider.readCommitted(owner, "probe-agent", -1, 10))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Test
    void explicitDatabaseJournalReopensWithoutAgentAndKeepsUserIsolation() {
        var owner = binding();
        var dialect = AbstractJdbcDialect.from(source).autoCreateTable(true).build();
        var distributed = JdbcDistributedStore.create(source, dialect);
        var first = new RuntimeJournalStore(distributed.baseStore());
        var context = RuntimeContext.builder().userId(owner.userId()).sessionId(owner.sessionId()).build();
        var key = new SessionKey(owner.userId(), "journal-agent", owner.sessionId());
        var log = first.open(key, context);
        assertThat(log.head().seq()).isZero();
        var writer = log.acquire("writer", Duration.ofSeconds(10));
        log.commit(writer, "journal-batch", 0, List.of(event(1, "database-journal", "{}")));
        log.release(writer);

        var reopened = new RuntimeJournalStore(JdbcDistributedStore.create(source, dialect).baseStore());
        var restored = reopened.open(key, context);
        assertThat(restored.readAfter(0, 10)).hasSize(1);
        assertThat(restored.head().owner()).isNull();
        assertThat(reopened.list(context)).contains(key);
        var anotherUser = RuntimeContext.builder().userId(UUID.randomUUID().toString()).sessionId(owner.sessionId()).build();
        assertThat(reopened.open(new SessionKey(anotherUser.getUserId(), "journal-agent", owner.sessionId()), anotherUser)
                .head().seq()).isZero();
        assertThat(reopened.list(anotherUser)).isEmpty();
        assertThat(reopened.open(new SessionKey(owner.userId(), "other-agent", owner.sessionId()), context)
                .head().seq()).isZero();
        assertThat(restored.head().seq()).isEqualTo(1);
    }

    @Test
    void explicitJournalRejectsMissingOrMismatchedIdentity() {
        var owner = binding();
        var dialect = AbstractJdbcDialect.from(source).autoCreateTable(true).build();
        var store = new RuntimeJournalStore(JdbcDistributedStore.create(source, dialect).baseStore());
        var key = new SessionKey(owner.userId(), "journal-agent", owner.sessionId());
        assertThatThrownBy(() -> store.open(key, RuntimeContext.empty())).isInstanceOf(IllegalArgumentException.class);
        var wrongUser = RuntimeContext.builder().userId(UUID.randomUUID().toString()).sessionId(owner.sessionId()).build();
        assertThatThrownBy(() -> store.open(key, wrongUser)).isInstanceOf(IllegalArgumentException.class);
        var wrongSession = RuntimeContext.builder().userId(owner.userId()).sessionId(UUID.randomUUID().toString()).build();
        assertThatThrownBy(() -> store.open(key, wrongSession)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void officialExporterAcknowledgesOnlyCommittedReceipts() {
        var owner = binding();
        var context = RuntimeContext.builder().userId(owner.userId()).sessionId(owner.sessionId()).build();
        var log = new InMemorySessionLogStore().open(new SessionKey(owner.userId(), "probe", owner.sessionId()), context);
        var writer = log.acquire("writer", Duration.ofSeconds(10));
        log.commit(writer, "batch", 0, List.of(event(1, "first", "{}"), event(2, "second", "{}")));
        log.release(writer);
        var broken = spy(mapper);
        doThrow(new IllegalStateException("收件故障注入")).when(broken).findCollisions(any());
        var failing = sink(transactionalService(broken), owner);
        assertThatThrownBy(() -> new SessionLogExporter(log, failing).drain()).isInstanceOf(IllegalStateException.class);
        assertThat(log.exportCursor(failing.name())).isZero();
        assertThat(count(owner)).isZero();

        var reliable = sink(receipts, owner);
        new SessionLogExporter(log, reliable).drain();
        assertThat(log.exportCursor(reliable.name())).isEqualTo(2);
        assertThat(count(owner)).isEqualTo(2);
        new SessionLogExporter(log, reliable).drain();
        assertThat(count(owner)).isEqualTo(2);
    }

    @Test
    void committedReceiptCanBeRepeatedWithoutStoringPayload() {
        var owner = binding();
        var event = event(1, "event-1", "{\"prompt\":\"PRIVATE_PROBE_CONTENT\"}");
        var sink = sink(receipts, owner);
        assertThat(sink.name()).isEqualTo("squad-execution-v2");
        sink.accept(event);
        sink.accept(event);
        assertThat(count(owner)).isEqualTo(1);
        var row = jdbc.queryForMap("SELECT * FROM sq_runtime_event_inbox WHERE binding_id=?", owner.bindingId());
        assertThat(row.toString()).doesNotContain("PRIVATE_PROBE_CONTENT", "prompt");
        assertThat(row.get("status")).isEqualTo("RECEIVED");
        assertThat(row.get("payload_digest").toString()).hasSize(64);
        jdbc.update("UPDATE sq_runtime_event_inbox SET status='APPLIED' WHERE binding_id=?", owner.bindingId());
        sink.accept(event);
        assertThat(jdbc.queryForObject("SELECT status FROM sq_runtime_event_inbox WHERE binding_id=?", String.class,
                owner.bindingId())).isEqualTo("APPLIED");
    }

    @Test
    void conflictingSequenceEventIdentityAndEnvelopeAreRejected() {
        var owner = binding();
        var sink = sink(receipts, owner);
        sink.accept(event(1, "same", "{}"));
        assertThatThrownBy(() -> sink.accept(event(1, "same", "{\"changed\":true}")))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("冲突");
        assertThatThrownBy(() -> sink.accept(event(2, "same", "{}")))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("冲突");
        assertThatThrownBy(() -> sink.accept(event(1, "different", "{}")))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("冲突");
        var changedType = new SessionEvent(1, "same", 1, 1, "model/end", "run", "turn", true, "{}");
        assertThatThrownBy(() -> sink.accept(changedType)).isInstanceOf(IllegalStateException.class);
        assertThat(count(owner)).isEqualTo(1);
    }

    @Test
    void wrongUserOrSessionCannotReceiveIntoAnotherBinding() {
        var owner = binding();
        var wrongUser = new RuntimeEventSource(UUID.randomUUID().toString(), owner.bindingId(), owner.sessionId(), 1);
        var wrongSession = new RuntimeEventSource(owner.userId(), owner.bindingId(), UUID.randomUUID().toString(), 1);
        assertThatThrownBy(() -> sink(receipts, wrongUser).accept(event(1, "wrong-user", "{}")))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("归属");
        assertThatThrownBy(() -> sink(receipts, wrongSession).accept(event(1, "wrong-session", "{}")))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("归属");
        assertThat(count(owner)).isZero();
    }

    @Test
    void failureAfterInsertRollsBackAndSinkDoesNotAcknowledge() {
        var owner = binding();
        var broken = spy(mapper);
        doThrow(new IllegalStateException("故障注入")).when(broken).findCollisions(any());
        var sink = sink(transactionalService(broken), owner);
        assertThatThrownBy(() -> sink.accept(event(1, "rollback", "{}")))
                .isInstanceOf(IllegalStateException.class).hasMessage("故障注入");
        assertThat(count(owner)).isZero();
        sink(receipts, owner).accept(event(1, "rollback", "{}"));
        assertThat(count(owner)).isEqualTo(1);
    }

    @Test
    void twoIndependentTransactionsDeduplicateConcurrentExport() throws Exception {
        var owner = binding();
        var first = sink(receipts, owner);
        var second = sink(transactionalService(mapper), owner);
        var start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var work = List.of(first, second).stream().map(sink -> executor.submit(() -> {
                if (!start.await(5, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("实验启动门闩超时");
                }
                sink.accept(event(1, "concurrent", "{}"));
                return true;
            })).toList();
            start.countDown();
            for (var future : work) {
                assertThat(future.get(10, TimeUnit.SECONDS)).isTrue();
            }
        }
        assertThat(count(owner)).isEqualTo(1);
    }

    @Test
    void unknownRequiredEventIsRetainedAsUnconsumedLocation() {
        var owner = binding();
        sink(receipts, owner).accept(event(3, "unknown", "{}"));
        assertThat(count(owner)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT native_required FROM sq_runtime_event_inbox WHERE binding_id=?",
                Boolean.class, owner.bindingId())).isTrue();
        assertThat(jdbc.queryForObject("SELECT status FROM sq_runtime_event_inbox WHERE binding_id=?", String.class,
                owner.bindingId())).isEqualTo("RECEIVED");
    }

    @Test
    void baselineAndIncrementalInboxDefinitionMatch() throws Exception {
        String baseline = Files.readString(RESOURCES.resolve("db/migration/V1__baseline.sql"));
        String schema = Files.readString(RESOURCES.resolve("db/schema.sql"));
        String migration = Files.readString(REPO.resolve("deploy/db/migrations/V6__runtime_event_inbox.sql"));
        String marker = "CREATE TABLE IF NOT EXISTS `sq_runtime_event_inbox`";
        assertThat(baseline.substring(baseline.indexOf(marker)).strip())
                .isEqualTo(migration.substring(migration.indexOf(marker)).strip());
        assertThat(schema.substring(schema.indexOf(marker)).strip())
                .isEqualTo(migration.substring(migration.indexOf(marker)).strip());
        assertThat(jdbc.queryForObject("SELECT table_comment FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name='sq_runtime_event_inbox'",
                String.class)).isNotBlank();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='sq_runtime_event_inbox' AND column_comment=''",
                Integer.class)).isZero();
    }

    private static RuntimeEventSink sink(RuntimeEventReceiptService service, RuntimeEventSource owner) {
        var factory = new RuntimeEventSinkFactory();
        ReflectionTestUtils.setField(factory, "receipts", service);
        return factory.bind(owner);
    }

    private static Path findRepository() {
        Path current = Path.of("").toAbsolutePath();
        while (current != null) {
            if (Files.isDirectory(current.resolve("server/agent-squad-server"))) {
                return current;
            }
            current = current.getParent();
        }
        throw new IllegalStateException("未找到实验源码目录");
    }

    private static RuntimeEventReceiptService transactionalService(RuntimeEventInboxMapper repository) {
        var service = new RuntimeEventReceiptService();
        ReflectionTestUtils.setField(service, "inboxMapper", repository);
        var advice = new TransactionInterceptor(new DataSourceTransactionManager(source),
                new AnnotationTransactionAttributeSource());
        var proxy = new ProxyFactory(service);
        proxy.addAdvice(advice);
        return (RuntimeEventReceiptService) proxy.getProxy();
    }

    private RuntimeEventSource binding() {
        var owner = new RuntimeEventSource(UUID.randomUUID().toString(), UUID.randomUUID().toString(),
                UUID.randomUUID().toString(), 1);
        jdbc.update("INSERT INTO sq_runtime_binding(binding_id,user_id,role,agent_id,framework_session_id,status) VALUES(?,?,'MASTER','probe',?,'IDLE')",
                owner.bindingId(), owner.userId(), owner.sessionId());
        return owner;
    }

    private long count(RuntimeEventSource owner) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM sq_runtime_event_inbox WHERE user_id=? AND binding_id=?",
                Long.class, owner.userId(), owner.bindingId());
    }

    private SessionEvent event(long sequence, String eventId, String payload) {
        return new SessionEvent(1, eventId, sequence, 1, "unknown/required", "run", "turn", true, payload);
    }
}
