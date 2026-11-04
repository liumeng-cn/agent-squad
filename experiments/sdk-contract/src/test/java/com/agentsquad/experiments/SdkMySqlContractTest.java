package com.agentsquad.experiments;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Files;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * MySQL持久确认崩溃窗口验证
 * <p>
 * 只在专用容器变量存在时运行，分别核对halt退出及新JVM的可靠收件、导出和幂等消费。
 */
@EnabledIfEnvironmentVariable(named = "SQUAD_PROBE_MYSQL_URL", matches = ".*/squad_sdk_probe\\?.*")
class SdkMySqlContractTest {
    @Test
    void recoversAfterInboxCommitBeforeExportAck(@TempDir Path directory) throws Exception {
        run(directory, "crash", 23);
        var store = new MySqlProbeStore(false);
        assertThat(store.count("probe_inbox")).isEqualTo(2);
        assertThat(store.count("probe_effect")).isZero();
        assertThat(run(directory, "resume", 0)).contains("cursor=3,inbox=3,effects=3,models=0");
        assertThat(run(directory, "resume", 0)).contains("cursor=3,inbox=3,effects=3,models=0");
        try (var paths = Files.walk(directory)) {
            assertThat(paths.noneMatch(path -> path.getFileName().toString().equals(".agentscope-runtime"))).isTrue();
        }
    }

    private String run(Path directory, String mode, int exit) throws Exception {
        var process = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                "-cp", System.getProperty("java.class.path"), MySqlJournalProbe.class.getName(),
                directory.resolve(mode + "-" + UUID.randomUUID()).toString(), mode).redirectErrorStream(true).start();
        try {
            assertThat(process.waitFor(30, TimeUnit.SECONDS)).isTrue();
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            assertThat(process.exitValue()).as(output).isEqualTo(exit);
            return output;
        } finally {
            if (process.isAlive()) {
                process.destroyForcibly();
            }
        }
    }
}
