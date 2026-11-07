package com.agentsquad.squad.integration.agentscope.journal;

import com.agentsquad.squad.integration.agentscope.inbox.RuntimeEventInboxMapper;
import com.agentsquad.squad.integration.agentscope.inbox.RuntimeEventSinkFactory;
import com.agentsquad.squad.integration.agentscope.inbox.RuntimeEventSource;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.session.SessionEvent;
import io.agentscope.core.session.SessionExportSink;
import io.agentscope.core.session.SessionKey;
import io.agentscope.core.session.SessionLogStore;
import io.agentscope.extensions.jdbc.JdbcDistributedStore;
import io.agentscope.extensions.jdbc.dialect.AbstractJdbcDialect;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import org.springframework.boot.sql.init.dependency.DependsOnDatabaseInitialization;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.util.List;

/**
 * 运行集成模块的数据库会话Provider
 * <p>
 * 统一装配固定用户、会话和持久收件回调，纯读入口直接读取官方journal，不创建Agent、不领取或续租Job。
 */
@Component
@DependsOnDatabaseInitialization
public class RuntimeSessionProvider {
    private static final int MAX_READ_BATCH = 1000;

    @Resource
    private DataSource dataSource;

    @Resource
    private RuntimeEventSinkFactory sinkFactory;

    @Resource
    private RuntimeEventInboxMapper inboxMapper;

    private RuntimeJournalStore journalStore;

    @PostConstruct
    public void initialize() {
        var dialect = AbstractJdbcDialect.from(dataSource).autoCreateTable(false).build();
        journalStore = new RuntimeJournalStore(JdbcDistributedStore.create(dataSource, dialect).baseStore());
    }

    /**
     * 为v2 Harness显式提供日志后端；不会扫描、启动或迁移已有本地会话。
     */
    public SessionLogStore logStore() {
        return journalStore;
    }

    /** 仅供停服后的显式导入命令使用，不参与正常运行与自动恢复。 */
    public io.agentscope.core.session.AtomicSessionStorage migrationStorage(RuntimeContext context) {
        return journalStore.migrationStorage(context);
    }

    /**
     * 装配可供首发、恢复和答复共用的基础上下文；身份来自持久Binding，业务权限仍由原授权服务裁决。
     */
    @Transactional(readOnly = true)
    public RuntimeContext executionContext(RuntimeEventSource source) {
        requireBinding(source);
        var context = readContext(source);
        context.put(RuntimeEventSource.class, source);
        context.put(SessionExportSink.CONTEXT_KEY, sinkFactory.bind(source));
        return context;
    }

    /**
     * 只读取已提交前缀；不启动dispatcher、不导出确认、不续租，也不因页面读取执行模型或工具。
     */
    @Transactional(readOnly = true)
    public List<SessionEvent> readCommitted(RuntimeEventSource source, String agentId, long after, int limit) {
        if (after < 0 || limit < 1 || limit > MAX_READ_BATCH) {
            throw new IllegalArgumentException("原生日志读取范围无效");
        }
        requireBinding(source);
        if (agentId == null || !agentId.equals(inboxMapper.findBoundAgent(source))) {
            throw new IllegalStateException("原生日志Agent与绑定不匹配");
        }
        return journalStore.open(new SessionKey(source.userId(), agentId, source.sessionId()), readContext(source))
                .readAfter(after, limit);
    }

    private RuntimeContext readContext(RuntimeEventSource source) {
        return RuntimeContext.builder().userId(source.userId()).sessionId(source.sessionId()).build();
    }

    private void requireBinding(RuntimeEventSource source) {
        if (source == null || !source.sessionId().equals(inboxMapper.findBoundSession(source))) {
            throw new IllegalStateException("原生会话归属不匹配");
        }
    }
}
