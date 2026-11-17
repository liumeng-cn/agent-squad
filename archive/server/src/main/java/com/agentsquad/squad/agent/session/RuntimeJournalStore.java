package com.agentsquad.squad.agent.session;

import com.agentsquad.squad.agent.RuntimeContextIds;

import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.session.SessionKey;
import io.agentscope.core.session.SessionLog;
import io.agentscope.core.session.SessionLogStore;
import io.agentscope.core.session.AtomicSessionStorage;
import io.agentscope.harness.agent.filesystem.remote.RemoteFilesystem;
import io.agentscope.harness.agent.filesystem.remote.store.BaseStore;
import io.agentscope.harness.agent.session.WorkspaceSessionLogStore;

import java.util.List;
import java.util.Objects;

/**
 * 数据库原生日志存储适配器
 * <p>
 * 显式使用官方远程原子存储，避免复合文件系统将journal路由到本地目录；只打开日志，不创建Agent或启动执行。
 */
public final class RuntimeJournalStore implements SessionLogStore {
    private static final String PLATFORM_NAMESPACE = "squad";
    private static final String JOURNAL_NAMESPACE = "native-journal-v2";

    private final SessionLogStore delegate;
    private final RemoteFilesystem filesystem;

    public RuntimeJournalStore(BaseStore store) {
        filesystem = new RemoteFilesystem(Objects.requireNonNull(store), RuntimeJournalStore::namespace);
        delegate = new WorkspaceSessionLogStore(filesystem);
    }

    /**
     * 离线导入只通过SDK原子存储搬运日志对象，不自行访问SDK数据库表。
     */
    public AtomicSessionStorage migrationStorage(RuntimeContext context) {
        requireUser(context);
        return filesystem.sessionStorage(context);
    }

    @Override
    public SessionLog open(SessionKey key, RuntimeContext context) {
        requireUser(context);
        if (!String.valueOf(RuntimeContextIds.userId(context)).equals(key.userId())
                || !Objects.equals(context.getSessionId(), key.sessionId())) {
            throw new IllegalArgumentException("原生日志读取身份与会话不匹配");
        }
        return delegate.open(key, context);
    }

    @Override
    public List<SessionKey> list(RuntimeContext context) {
        requireUser(context);
        return delegate.list(context);
    }

    private static List<String> namespace(RuntimeContext context) {
        requireUser(context);
        return List.of(PLATFORM_NAMESPACE, JOURNAL_NAMESPACE, String.valueOf(RuntimeContextIds.userId(context)));
    }

    private static void requireUser(RuntimeContext context) {
        if (context == null) {
            throw new IllegalArgumentException("原生日志必须显式指定归属用户");
        }
    }
}
