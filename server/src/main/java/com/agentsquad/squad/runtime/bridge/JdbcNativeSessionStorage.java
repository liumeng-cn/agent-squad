package com.agentsquad.squad.runtime.bridge;

import io.agentscope.core.session.AtomicSessionStorage;
import jakarta.annotation.Resource;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * AgentScope 原生日志的 MySQL 对象存储。每个对象以数据库唯一键和版本条件更新实现跨进程 CAS。
 *
 * <p>只保存框架原始字节；业务任务、命令和公开事件不从这里反向推导。</p>
 */
@Component
public class JdbcNativeSessionStorage implements AtomicSessionStorage {

    @Resource
    private JdbcTemplate jdbcTemplate;

    /** 读取对象版本与完整字节；不存在返回 null。 */
    @Override
    public Value read(String path) {
        return jdbcTemplate.query("SELECT object_version, object_bytes FROM sq_native_session_object WHERE object_path = ?",
                result -> result.next() ? new Value(result.getLong(1), result.getBytes(2)) : null,
                path);
    }

    /** 只有期望版本仍为当前版本时才持久化；0 表示首次创建。 */
    @Override
    public boolean compareAndSet(String path, long expectedVersion, byte[] bytes) {
        if (expectedVersion == 0) {
            try {
                jdbcTemplate.update("INSERT INTO sq_native_session_object (object_path, object_version, object_bytes) VALUES (?, 1, ?)",
                        path, bytes);
                return true;
            } catch (DuplicateKeyException ignored) {
                return false;
            }
        }
        return jdbcTemplate.update("UPDATE sq_native_session_object SET object_version = object_version + 1, object_bytes = ? "
                        + "WHERE object_path = ? AND object_version = ?",
                bytes, path, expectedVersion) == 1;
    }
}
