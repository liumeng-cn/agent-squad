package com.agentsquad.squad.runtime;

import com.agentsquad.squad.AgentSquadApplication;
import com.agentsquad.squad.dto.MessageCreateDTO;
import com.agentsquad.squad.service.MessageAcceptanceService;
import com.agentsquad.squad.service.RedisCommandWakeup;
import com.agentsquad.squad.testsupport.MySqlTestDatabase;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.SpringApplication;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;

import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Redis就绪引用驱动真实回复执行
 * <p>
 * 使用隔离MySQL、Redis和本地模型；关闭高频数据库扫描，验证提交后通知、重复引用与通知故障降级。
 */
class RedisCommandWakeupIntegrationTest {
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void redisDeliversCommittedCommandAndDuplicateDoesNotRepeatModel(boolean abandonedPending, @TempDir Path directory) throws Exception {
        String namespace = "squad:test:" + UUID.randomUUID();
        try (var model = new NaturalReplyModelStub();
             var context = SpringApplication.run(AgentSquadApplication.class,
                     "--spring.main.web-application-type=none",
                     "--spring.datasource.url=" + MySqlTestDatabase.url(directory),
                     "--spring.datasource.username=" + MySqlTestDatabase.username(),
                     "--spring.datasource.password=" + MySqlTestDatabase.password(),
                     "--agentsquad.delivery.enabled=true", "--agentsquad.delivery.poll-delay-ms=600000",
                     "--agentsquad.projection.poll-delay-ms=600000", "--agentsquad.redis-wakeup.enabled=true",
                     "--agentsquad.projection.wakeup-retry-delay-ms=15000",
                     "--agentsquad.redis-wakeup.projection-poll-delay-ms=100",
                     "--agentsquad.redis-wakeup.namespace=" + namespace,
                     "--agentsquad.redis-wakeup.poll-delay-ms=" + (abandonedPending ? 600000 : 100),
                     "--agentsquad.redis-wakeup.compensation-delay-ms=600000",
                     "--agentsquad.redis-wakeup.reclaim-idle-ms=1000",
                     "--spring.data.redis.host=127.0.0.1",
                     "--spring.data.redis.port=" + System.getenv("SQUAD_TEST_REDIS_PORT"),
                     "--spring.data.redis.password=" + System.getenv("SQUAD_TEST_REDIS_PASSWORD"),
                     "--spring.data.redis.timeout=200ms", "--spring.data.redis.connect-timeout=200ms",
                     "--agentsquad.model.api-key=test-only", "--agentsquad.model.base-url=" + model.baseUrl(),
                     "--agentsquad.model.name=test-model", "--agentsquad.worker.model-profiles.default.api-key=test-only",
                     "--agentsquad.worker.model-profiles.default.base-url=" + model.baseUrl(),
                     "--agentsquad.worker.model-profiles.default.model-name=test-model")) {
            var jdbc = context.getBean(JdbcTemplate.class);
            var redis = context.getBean(StringRedisTemplate.class);
            var wakeup = context.getBean(RedisCommandWakeup.class);
            String user = "00000000-0000-0000-0000-000000000001";
            var accepted = context.getBean(MessageAcceptanceService.class).accept(user, null, "redis-first",
                    new MessageCreateDTO("测试Redis命令唤醒", List.of()));
            if (abandonedPending) {
                String queuedStream = namespace + ":{p0}:COMMAND:ready";
                var pending = redis.opsForStream().read(org.springframework.data.redis.connection.stream.Consumer.from("command-delivery-v1", "crashed-consumer"),
                        org.springframework.data.redis.connection.stream.StreamReadOptions.empty().count(1),
                        org.springframework.data.redis.connection.stream.StreamOffset.create(queuedStream,
                                org.springframework.data.redis.connection.stream.ReadOffset.lastConsumed()));
                assertThat(pending).hasSize(1);
                assertThat(model.calls.get()).isZero();
                Thread.sleep(1100);
                wakeup.receive();
            }
            assertThat(model.entered.await(10, TimeUnit.SECONDS)).isTrue();
            String command = jdbc.queryForObject("SELECT command_id FROM sq_command WHERE action='MESSAGE_SUBMIT'", String.class);
            for (int index = 0; index < 8; index++) {
                assertThat(wakeup.publish(command)).isTrue();
            }
            wakeup.receive();
            model.firstChunk.countDown();
            model.release.countDown();
            ResponseNativeExecutionIntegrationTest.await(context, accepted, "SUCCEEDED");
            var projectionGroup = redis.opsForStream().groups(namespace + ":{p0}:PROJECTION:ready").iterator().next();
            assertThat(projectionGroup.groupName()).isEqualTo("projection-v1");
            assertThat(projectionGroup.lastDeliveredId()).isNotEqualTo("0-0");
            assertThat(model.calls.get()).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sq_message WHERE role='ASSISTANT'", Integer.class)).isEqualTo(1);

            String stream = namespace + ":{p0}:COMMAND:ready";
            for (int attempt = 0; attempt < 100 && redis.opsForStream().size(stream) != 0; attempt++) {
                Thread.sleep(50);
            }
            assertThat(redis.opsForStream().size(stream)).isZero();

            // 模拟发布连接不可用：原业务事务不能回滚，且无需等待十分钟补偿周期。
            var factory = (org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory) redis.getConnectionFactory();
            factory.stop();
            var fallback = context.getBean(MessageAcceptanceService.class).accept(user, accepted.conversationId(), "redis-down",
                    new MessageCreateDTO("测试通知失败降级", List.of()));
            ResponseNativeExecutionIntegrationTest.await(context, fallback, "SUCCEEDED");
            assertThat(model.calls.get()).isEqualTo(2);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sq_message WHERE role='ASSISTANT'", Integer.class)).isEqualTo(2);
        }
    }
}
