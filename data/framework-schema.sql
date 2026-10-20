-- AgentScope Java v2.1.0-BETA1 / 5451c73abe47053340683a1cc6d67b290b056648
-- MysqlDialect 基础表结构参考；前缀 as_，补中文注释，未执行，不能直接视作已验证迁移。
-- 与自有业务表分开统计，不增加业务审计列或改动SDK键语义。
CREATE TABLE `as_store` (
    `namespace_path` VARCHAR(512) COLLATE utf8mb4_bin NOT NULL COMMENT '框架逻辑命名空间路径',
    `item_key` VARCHAR(255) COLLATE utf8mb4_bin NOT NULL COMMENT '框架对象键',
    `value_json` LONGTEXT NOT NULL COMMENT '框架序列化值；日志字节以Base64封装',
    `version` BIGINT NOT NULL COMMENT '原子CAS版本',
    `updated_at` BIGINT NOT NULL COMMENT 'SDK时间戳',
    PRIMARY KEY (`namespace_path`, `item_key`),
    KEY `idx_namespace` (`namespace_path`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AgentScope共享KV及原生日志对象';

CREATE TABLE `as_sessions` (
    `session_id` VARCHAR(255) COLLATE utf8mb4_bin NOT NULL COMMENT '框架状态会话键',
    `state_key` VARCHAR(255) COLLATE utf8mb4_bin NOT NULL COMMENT '状态分片键',
    `item_index` INT NOT NULL DEFAULT 0 COMMENT '状态项索引',
    `state_data` LONGTEXT NOT NULL COMMENT 'SDK状态负载；不是平台可改写的任务状态',
    `version` BIGINT NOT NULL DEFAULT 0 COMMENT '状态CAS版本',
    `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`session_id`, `state_key`, `item_index`),
    KEY `idx_session` (`session_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AgentScope状态存储组件表';

CREATE TABLE `as_snapshots` (
    `snapshot_id` VARCHAR(512) COLLATE utf8mb4_bin NOT NULL COMMENT '沙箱快照标识',
    `data` LONGBLOB NOT NULL COMMENT '快照字节',
    `created_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '快照更新时间',
    PRIMARY KEY (`snapshot_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AgentScope JDBC沙箱快照';
