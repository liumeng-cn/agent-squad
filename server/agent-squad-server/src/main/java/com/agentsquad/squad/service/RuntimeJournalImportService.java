package com.agentsquad.squad.service;

import com.agentsquad.squad.integration.agentscope.journal.RuntimeSessionProvider;

import com.agentsquad.squad.integration.agentscope.journal.RuntimeJournalImportResult;

import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.session.AtomicSessionStorage;
import io.agentscope.core.session.SessionKey;
import io.agentscope.core.session.SessionLog;
import io.agentscope.harness.agent.filesystem.local.LocalFilesystem;
import io.agentscope.harness.agent.session.WorkspaceSessionLogStore;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

/**
 * 停服后的本地原生日志导入
 * <p>
 * 通过官方SDK原子存储复制会话、输入队列、检查点、载荷及导出水位；不执行SQL、不启动Agent、不删除来源。
 * 目标对象仅允许首次创建或内容完全相同的重放，遇到差异立即停止，禁止覆盖已经推进的数据库历史。
 */
@Service
public class RuntimeJournalImportService {
    @Resource
    private RuntimeSessionProvider sessions;

    /**
     * 调用前必须停止旧服务。逐会话先复制内容、最后复制head，并逐事件核对；部分失败可在来源不变时重跑。
     */
    public RuntimeJournalImportResult importWorkspace(Path root) throws IOException {
        if (!Files.isDirectory(root)) {
            throw new IllegalArgumentException("旧日志目录不存在");
        }
        int users = 0;
        int imported = 0;
        long objects = 0;
        long bytes = 0;
        List<Path> directories;
        try (var entries = Files.list(root)) {
            directories = entries.filter(Files::isDirectory)
                    .filter(path -> path.getFileName().toString().matches("[a-fA-F0-9-]{36}"))
                    .sorted().toList();
        }
        for (Path userRoot : directories) {
            String user = userRoot.getFileName().toString();
            var context = RuntimeContext.builder().userId(user).build();
            var filesystem = new LocalFilesystem(userRoot);
            var source = new WorkspaceSessionLogStore(filesystem);
            var keys = source.list(context);
            if (keys.isEmpty()) {
                continue;
            }
            users++;
            var sourceStorage = filesystem.sessionStorage(context);
            var targetStorage = sessions.migrationStorage(context);
            for (SessionKey key : keys) {
                var identity = RuntimeContext.builder().userId(user).sessionId(key.sessionId()).build();
                SessionLog original = source.open(key, identity);
                requireStopped(original);
                requireStopped(original.inbox());
                var paths = sourceStorage.listPaths(key.storagePath() + "/").stream()
                        .filter(path -> !path.endsWith(".lock") && !path.endsWith(".tmp"))
                        .sorted(Comparator.comparing((String path) -> path.endsWith("/head.json"))
                                .thenComparing(Comparator.naturalOrder())).toList();
                for (String path : paths) {
                    var value = sourceStorage.read(path);
                    if (value == null) {
                        throw new IllegalStateException("导入期间来源对象消失，请确认旧服务已经停止");
                    }
                    copy(sourceStorage, targetStorage, path, value);
                    objects++;
                    bytes += value.bytes().length;
                }
                SessionLog restored = sessions.logStore().open(key, identity);
                verify(original, restored);
                verify(original.inbox(), restored.inbox());
                imported++;
            }
        }
        return new RuntimeJournalImportResult(users, imported, objects, bytes);
    }

    private void requireStopped(SessionLog log) {
        var head = log.head();
        if (head.owner() != null && head.leaseUntil() > System.currentTimeMillis()) {
            throw new IllegalStateException("来源仍有有效写入者，请停止旧服务并等待租约释放后导入");
        }
    }

    private void copy(AtomicSessionStorage source, AtomicSessionStorage target, String path,
                      AtomicSessionStorage.Value value) {
        var existing = target.read(path);
        if (existing == null) {
            target.compareAndSet(path, 0, value.bytes());
            existing = target.read(path);
        }
        if (existing == null || !Arrays.equals(existing.bytes(), value.bytes())) {
            throw new IllegalStateException("数据库日志与来源不同，拒绝覆盖：" + path);
        }
        var current = source.read(path);
        if (current == null || current.version() != value.version()) {
            throw new IllegalStateException("导入期间来源发生变化，请停止旧服务后重试");
        }
    }

    private void verify(SessionLog source, SessionLog target) {
        long through = source.head().seq();
        if (through != target.head().seq()) {
            throw new IllegalStateException("导入后的日志水位不一致");
        }
        long cursor = 0;
        while (cursor < through) {
            var expected = source.readAfter(cursor, 128);
            var actual = target.readAfter(cursor, 128);
            if (expected.isEmpty() || !expected.equals(actual)) {
                throw new IllegalStateException("导入后的事件内容或顺序不一致");
            }
            cursor = expected.get(expected.size() - 1).seq();
        }
    }
}
