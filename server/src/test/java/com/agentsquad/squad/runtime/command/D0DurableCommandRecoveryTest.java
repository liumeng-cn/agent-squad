package com.agentsquad.squad.runtime.command;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * D0 故障探针：验证持久受理、重启恢复和重复提交语义。
 *
 * <p>测试中的第二个 FileCommandStore 代表进程重启后的新运行实例。</p>
 */
class D0DurableCommandRecoveryTest {

    @Test
    void shouldRecoverAcceptedCommandAndExecuteItOnlyOnce(@TempDir Path tempDirectory) throws Exception {
        Path file = tempDirectory.resolve("commands.log");
        FileCommandStore firstProcess = new FileCommandStore(file);

        DurableCommand accepted = firstProcess.accept("cmd-001", "start-agent");
        DurableCommand duplicate = firstProcess.accept("cmd-001", "start-agent");

        assertThat(accepted.status()).isEqualTo(CommandStatus.ACCEPTED);
        assertThat(duplicate).isEqualTo(accepted);

        FileCommandStore restartedProcess = new FileCommandStore(file);
        List<DurableCommand> pending = restartedProcess.pending();
        assertThat(pending).extracting(DurableCommand::commandId).containsExactly("cmd-001");

        DurableCommand executed = restartedProcess.markExecuted("cmd-001");
        assertThat(executed.status()).isEqualTo(CommandStatus.EXECUTED);
        assertThat(restartedProcess.markExecuted("cmd-001")).isEqualTo(executed);
        assertThat(restartedProcess.pending()).isEmpty();
    }

    @Test
    void shouldRejectSameCommandWithDifferentPayload(@TempDir Path tempDirectory) throws Exception {
        FileCommandStore store = new FileCommandStore(tempDirectory.resolve("commands.log"));
        store.accept("cmd-002", "first-input");

        assertThatThrownBy(() -> store.accept("cmd-002", "changed-input"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("输入不能变化");
    }
}
