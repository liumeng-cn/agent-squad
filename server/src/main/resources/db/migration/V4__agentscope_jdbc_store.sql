-- AgentScope 2.1.0-BETA1 JDBC 方言的基础表，由 Flyway 管理，不授权框架在启动时建表。
-- 原生日志对象路径可能超过该版本默认的 item_key VARCHAR(255)。
-- 两段主键总长 767 字符，满足 InnoDB utf8mb4 的 3072 字节上限。
CREATE TABLE agentscope_store
(
    namespace_path VARCHAR(255) COLLATE utf8mb4_bin NOT NULL COMMENT 'AgentScope 用户隔离与存储分区',
    item_key       VARCHAR(512) COLLATE utf8mb4_bin NOT NULL COMMENT 'Workspace 与原生日志对象路径',
    value_json     LONGTEXT                         NOT NULL COMMENT '框架原生存储对象',
    version        BIGINT                           NOT NULL COMMENT '框架 CAS 版本',
    updated_at     BIGINT                           NOT NULL COMMENT '框架更新时间戳',
    PRIMARY KEY (namespace_path, item_key),
    KEY            idx_agentscope_namespace (namespace_path)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='AgentScope JDBC 共享存储';

CREATE TABLE agentscope_sessions
(
    session_id VARCHAR(255) COLLATE utf8mb4_bin NOT NULL COMMENT '框架会话标识',
    state_key  VARCHAR(255) COLLATE utf8mb4_bin NOT NULL COMMENT '框架状态键',
    item_index INT                              NOT NULL DEFAULT 0 COMMENT '状态项序号',
    state_data LONGTEXT                         NOT NULL COMMENT '框架状态内容',
    version    BIGINT                           NOT NULL DEFAULT 0 COMMENT '状态 CAS 版本',
    created_at DATETIME                                  DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME                                  DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (session_id, state_key, item_index),
    KEY        idx_agentscope_session (session_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='AgentScope 会话状态';

CREATE TABLE agentscope_snapshots
(
    snapshot_id VARCHAR(512) COLLATE utf8mb4_bin NOT NULL COMMENT '框架沙箱快照标识',
    data        LONGBLOB                         NOT NULL COMMENT '沙箱快照字节',
    created_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (snapshot_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='AgentScope 沙箱快照';
