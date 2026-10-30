package com.agentsquad.squad.sandbox;

import com.agentsquad.squad.sandbox.dto.SandboxExecuteRequest;
import com.agentsquad.squad.sandbox.dto.SandboxFile;
import com.agentsquad.squad.sandbox.infrastructure.HarnessDockerSandboxDriver;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 官方Docker驱动的隔离验证。
 * <p>
 * 只有显式指定已有镜像摘要才运行真实容器；默认禁用验证不依赖Docker。
 */
class HarnessDockerSandboxDriverTest {
    @Test
    void disabledOrMutableImageCannotExecute() {
        HarnessDockerSandboxDriver driver = new HarnessDockerSandboxDriver();
        assertThatThrownBy(driver::requireAvailable).hasMessageContaining("CAPABILITY_UNAVAILABLE");
        ReflectionTestUtils.setField(driver, "enabled", true);
        ReflectionTestUtils.setField(driver, "image", "python:latest");
        assertThatThrownBy(driver::requireAvailable).hasMessageContaining("CAPABILITY_UNAVAILABLE");
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "SQUAD_SANDBOX_TEST_IMAGE", matches = "sha256:[0-9a-f]{64}")
    void actualContainerEnforcesPolicyAndReturnsOnlyNamedFiles() throws Exception {
        String script = """
                import json, os, pathlib, socket
                facts = {}
                for key, filename in [('memory','memory.max'),('pids','pids.max'),('cpu','cpu.max')]:
                    facts[key] = pathlib.Path('/sys/fs/cgroup/' + filename).read_text().strip()
                facts['uid'] = os.getuid()
                facts['socket_exposed'] = pathlib.Path('/var/run/docker.sock').exists()
                facts['host_home'] = pathlib.Path('/Users/liumeng').exists()
                facts['nonewprivs'] = 'NoNewPrivs:\\t1' in pathlib.Path('/proc/self/status').read_text()
                try:
                    pathlib.Path('/etc/agentsquad-probe').write_text('forbidden')
                    facts['readonly'] = False
                except OSError:
                    facts['readonly'] = True
                try:
                    socket.create_connection(('1.1.1.1', 53), timeout=0.3)
                    facts['network_blocked'] = False
                except OSError:
                    facts['network_blocked'] = True
                pathlib.Path('report.json').write_text(json.dumps(facts))
                print('isolated')
                """;
        var result = driver().execute(new SandboxExecuteRequest("policy", script, List.of(), List.of("report.json"), 10));
        assertThat(result.exitCode()).isZero();
        assertThat(result.stdout()).contains("isolated");
        String facts = new String(result.outputs().getFirst().content(), StandardCharsets.UTF_8);
        assertThat(facts).contains("134217728", "100000 100000", "\"pids\": \"64\"", "65534",
                "\"socket_exposed\": false", "\"host_home\": false", "\"nonewprivs\": true",
                "\"readonly\": true", "\"network_blocked\": true");
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "SQUAD_SANDBOX_TEST_IMAGE", matches = "sha256:[0-9a-f]{64}")
    void actualTimeoutAndSymlinkCannotEscapeAndOutputIsBounded() throws Exception {
        assertThatThrownBy(() -> driver().execute(new SandboxExecuteRequest("timeout", "while True: pass",
                List.of(), List.of(), 1))).hasMessageContaining("timed out");
        assertThatThrownBy(() -> driver().execute(new SandboxExecuteRequest("link",
                "import os; os.symlink('/etc/passwd','result.txt')", List.of(), List.of("result.txt"), 5)))
                .isInstanceOf(Exception.class);
        var result = driver().execute(new SandboxExecuteRequest("bounded", "print('x'*100000)",
                List.of(new SandboxFile("input.txt", "data")), List.of(), 5));
        assertThat(result.stdout().getBytes(StandardCharsets.UTF_8).length).isLessThanOrEqualTo(65536);
        assertThat(result.truncated()).isTrue();
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "SQUAD_SANDBOX_TEST_IMAGE", matches = "sha256:[0-9a-f]{64}")
    void actualMemoryAndProcessLimitsAreEnforced() throws Exception {
        var memory = driver().execute(new SandboxExecuteRequest("memory-limit", "data=bytearray(256*1024*1024)",
                List.of(), List.of(), 10));
        assertThat(memory.exitCode()).isNotZero();
        String script = """
                import subprocess
                children=[]
                blocked=False
                try:
                    for i in range(100):
                        try:
                            children.append(subprocess.Popen(['sleep','5']))
                        except OSError:
                            blocked=True
                            break
                finally:
                    for child in children:
                        child.terminate()
                    for child in children:
                        child.wait()
                print('pids-blocked=' + str(blocked))
                print('spawned=' + str(len(children)))
                """;
        var pids = driver().execute(new SandboxExecuteRequest("pids-limit", script, List.of(), List.of(), 10));
        assertThat(pids.exitCode()).isZero();
        assertThat(pids.stdout()).contains("pids-blocked=True");
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "SQUAD_SANDBOX_TEST_IMAGE", matches = "sha256:[0-9a-f]{64}")
    void detachedWritersAreKilledBeforeExtraction() throws Exception {
        String script = """
                import os,time,pathlib
                pathlib.Path('result.txt').write_text('stable')
                pid=os.fork()
                if pid==0:
                    os.setsid()
                    pid=os.fork()
                    if pid==0:
                        time.sleep(0.2)
                        pathlib.Path('result.txt').write_text('tampered')
                        time.sleep(10)
                    os._exit(0)
                """;
        var result = driver().execute(new SandboxExecuteRequest("detached", script, List.of(), List.of("result.txt"), 5));
        assertThat(new String(result.outputs().getFirst().content(), StandardCharsets.UTF_8)).isEqualTo("stable");
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "SQUAD_SANDBOX_TEST_IMAGE", matches = "sha256:[0-9a-f]{64}")
    void logSymlinkAndFifoAreRejectedInsteadOfUnboundedRead() {
        for (String operation : List.of("os.symlink('/dev/zero','__agentsquad_stdout__')",
                "os.mkfifo('__agentsquad_stdout__')")) {
            String script = "import os; os.unlink('__agentsquad_stdout__'); " + operation;
            assertThatThrownBy(() -> driver().execute(new SandboxExecuteRequest("log-tamper", script,
                    List.of(), List.of(), 5))).isInstanceOf(Exception.class);
        }
    }

    private HarnessDockerSandboxDriver driver() {
        HarnessDockerSandboxDriver driver = new HarnessDockerSandboxDriver();
        ReflectionTestUtils.setField(driver, "enabled", true);
        ReflectionTestUtils.setField(driver, "image", System.getenv("SQUAD_SANDBOX_TEST_IMAGE"));
        return driver;
    }
}
