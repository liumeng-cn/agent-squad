package com.agentsquad.squad.runtime;

import com.agentsquad.squad.AgentSquadApplication;
import com.agentsquad.squad.testsupport.PostgresTestDatabase;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 显式启用的浏览器联调夹具
 * <p>
 * 只连接本次隔离MySQL和本地模型，回环端口与受控释放文件供人工浏览器验证；不使用真实账号、模型或开发数据。
 */
@EnabledIfEnvironmentVariable(named = "SQUAD_BROWSER_PROBE", matches = "true")
class BrowserChatProbeTest {
    @Test
    void servesRealChatUntilBrowserReleasesFinal(@TempDir Path directory) throws Exception {
        Path release = Path.of("/tmp/squad-browser-release");
        Path ready = Path.of("/tmp/squad-browser-probe.json");
        Files.deleteIfExists(release);
        Files.deleteIfExists(Path.of("/tmp/squad-browser-done"));
        try (var model = new NaturalReplyModelStub();
             var context = SpringApplication.run(AgentSquadApplication.class,
                     "--server.port=0", "--server.shutdown=immediate", "--agentsquad.identity.allow-unsigned-local=true",
                     "--spring.datasource.url=" + PostgresTestDatabase.url(directory),
                     "--spring.datasource.username=" + PostgresTestDatabase.username(),
                     "--spring.datasource.password=" + PostgresTestDatabase.password(),
                     "--agentsquad.delivery.enabled=true", "--agentsquad.delivery.poll-delay-ms=3600000",
                     "--agentsquad.projection.poll-delay-ms=3600000", "--agentsquad.events.reconcile-delay-ms=3600000",
                     "--agentsquad.model.api-key=test-only", "--agentsquad.model.base-url=" + model.baseUrl(),
                     "--agentsquad.model.name=test-model", "--agentsquad.worker.model-profiles.default.api-key=test-only",
                     "--agentsquad.worker.model-profiles.default.base-url=" + model.baseUrl(),
                     "--agentsquad.worker.model-profiles.default.model-name=test-model")) {
            int port = ((ServletWebServerApplicationContext) context).getWebServer().getPort();
            Files.writeString(ready, "{\"port\":" + port + "}");
            assertThat(model.entered.await(600, TimeUnit.SECONDS)).isTrue();
            Thread.sleep(2000);
            model.firstChunk.countDown();
            for (int i = 0; i < 6000 && !Files.exists(release); i++) {
                Thread.sleep(100);
            }
            assertThat(Files.exists(release)).isTrue();
            model.release.countDown();
            var jdbc = context.getBean(JdbcTemplate.class);
            int completed = 0;
            for (int i = 0; i < 200; i++) {
                completed = jdbc.queryForObject("SELECT COUNT(*) FROM sq_response_execution WHERE status='SUCCEEDED'", Integer.class);
                if (completed == 1) break;
                Thread.sleep(50);
            }
            assertThat(completed).isEqualTo(1);
            assertThat(model.calls.get()).isEqualTo(1);
            // 留时间让浏览器确认终稿及页面刷新后的持久快照。
            for (int i = 0; i < 3000 && !Files.exists(Path.of("/tmp/squad-browser-done")); i++) {
                Thread.sleep(100);
            }
        } finally {
            Files.deleteIfExists(ready);
            Files.deleteIfExists(release);
        }
    }
}
