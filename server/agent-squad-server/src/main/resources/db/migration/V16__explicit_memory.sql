CREATE TABLE sq_memory
(
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '存储主键ID',
    memory_id CHAR(36) NOT NULL COMMENT '记忆业务ID',
    user_id CHAR(36) NOT NULL COMMENT '归属用户ID',
    memory_key VARCHAR(100) NOT NULL COMMENT '用户内唯一记忆键',
    content TEXT NOT NULL COMMENT '显式记忆正文，删除后清空',
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE' COMMENT '记忆状态[ACTIVE,DELETED]',
    version BIGINT NOT NULL DEFAULT 0 COMMENT '记忆内容乐观版本',
    expires_at DATETIME(3) NULL COMMENT '可选失效时间',
    source_message_id CHAR(36) NULL COMMENT '工具写入的受权用户消息ID，设置页写入为空',
    source_quote TEXT NULL COMMENT '用户显式授权原话片段，删除后清空',
    creator_id VARCHAR(36) NOT NULL DEFAULT '' COMMENT '创建人ID',
    creator_name VARCHAR(50) NOT NULL DEFAULT '' COMMENT '创建人姓名',
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    last_updater_id VARCHAR(36) NOT NULL DEFAULT '' COMMENT '最后更新人ID',
    last_updater_name VARCHAR(50) NOT NULL DEFAULT '' COMMENT '最后更新人姓名',
    last_update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '最后更新时间',
    del_flag CHAR(1) NOT NULL DEFAULT '0' COMMENT '删除标志[0-否,1-是]',
    PRIMARY KEY (id),
    UNIQUE KEY uk_memory_identity (user_id, memory_id),
    UNIQUE KEY uk_memory_key (user_id, memory_key),
    KEY idx_memory_active (user_id, status, id),
    CONSTRAINT ck_memory_status CHECK (status IN ('ACTIVE','DELETED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='用户显式长期记忆及删除墓碑';

CREATE TABLE sq_memory_revision
(
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '存储主键ID',
    user_id CHAR(36) NOT NULL COMMENT '归属用户ID',
    revision BIGINT NOT NULL DEFAULT 0 COMMENT '纠正和删除推进的上下文失效代际',
    creator_id VARCHAR(36) NOT NULL DEFAULT '' COMMENT '创建人ID',
    creator_name VARCHAR(50) NOT NULL DEFAULT '' COMMENT '创建人姓名',
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    last_updater_id VARCHAR(36) NOT NULL DEFAULT '' COMMENT '最后更新人ID',
    last_updater_name VARCHAR(50) NOT NULL DEFAULT '' COMMENT '最后更新人姓名',
    last_update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '最后更新时间',
    del_flag CHAR(1) NOT NULL DEFAULT '0' COMMENT '删除标志[0-否,1-是]',
    PRIMARY KEY (id),
    UNIQUE KEY uk_memory_revision_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='用户记忆失效代际';

CREATE TABLE sq_memory_scope
(
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '存储主键ID',
    user_id CHAR(36) NOT NULL COMMENT '归属用户ID',
    scope_id CHAR(36) NOT NULL COMMENT '受权执行作用域业务ID',
    memory_revision BIGINT NOT NULL COMMENT '冻结时用户记忆失效代际',
    barrier_status VARCHAR(16) NOT NULL DEFAULT 'CURRENT' COMMENT '上下文屏障[CURRENT,BLOCKED]',
    creator_id VARCHAR(36) NOT NULL DEFAULT '' COMMENT '创建人ID',
    creator_name VARCHAR(50) NOT NULL DEFAULT '' COMMENT '创建人姓名',
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    last_updater_id VARCHAR(36) NOT NULL DEFAULT '' COMMENT '最后更新人ID',
    last_updater_name VARCHAR(50) NOT NULL DEFAULT '' COMMENT '最后更新人姓名',
    last_update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '最后更新时间',
    del_flag CHAR(1) NOT NULL DEFAULT '0' COMMENT '删除标志[0-否,1-是]',
    PRIMARY KEY (id),
    UNIQUE KEY uk_memory_scope (user_id, scope_id),
    CONSTRAINT fk_memory_scope_owner FOREIGN KEY (user_id, scope_id) REFERENCES sq_execution_scope (user_id, scope_id),
    CONSTRAINT ck_memory_scope_barrier CHECK (barrier_status IN ('CURRENT','BLOCKED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='执行固定的记忆授权视图';

CREATE TABLE sq_memory_scope_item
(
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '存储主键ID',
    user_id CHAR(36) NOT NULL COMMENT '归属用户ID',
    scope_id CHAR(36) NOT NULL COMMENT '执行作用域业务ID',
    memory_id CHAR(36) NOT NULL COMMENT '记忆业务ID',
    memory_version BIGINT NOT NULL COMMENT '冻结记忆内容版本',
    creator_id VARCHAR(36) NOT NULL DEFAULT '' COMMENT '创建人ID',
    creator_name VARCHAR(50) NOT NULL DEFAULT '' COMMENT '创建人姓名',
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    last_updater_id VARCHAR(36) NOT NULL DEFAULT '' COMMENT '最后更新人ID',
    last_updater_name VARCHAR(50) NOT NULL DEFAULT '' COMMENT '最后更新人姓名',
    last_update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '最后更新时间',
    del_flag CHAR(1) NOT NULL DEFAULT '0' COMMENT '删除标志[0-否,1-是]',
    PRIMARY KEY (id),
    UNIQUE KEY uk_memory_scope_item (user_id, scope_id, memory_id),
    CONSTRAINT fk_memory_item_scope FOREIGN KEY (user_id, scope_id) REFERENCES sq_memory_scope (user_id, scope_id),
    CONSTRAINT fk_memory_item_record FOREIGN KEY (user_id, memory_id) REFERENCES sq_memory (user_id, memory_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='执行固定的记忆版本引用';

CREATE TABLE sq_memory_write_receipt
(
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '存储主键ID',
    user_id CHAR(36) NOT NULL COMMENT '归属用户ID',
    scope_id CHAR(36) NOT NULL COMMENT '来源回复执行作用域业务ID',
    logical_key VARCHAR(160) NOT NULL COMMENT '同执行内稳定工具逻辑键',
    payload_hash CHAR(64) NOT NULL COMMENT '规范化工具输入摘要',
    result_json JSON NOT NULL COMMENT '工具持久收据，删除正文不在此重复保存',
    creator_id VARCHAR(36) NOT NULL DEFAULT '' COMMENT '创建人ID',
    creator_name VARCHAR(50) NOT NULL DEFAULT '' COMMENT '创建人姓名',
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    last_updater_id VARCHAR(36) NOT NULL DEFAULT '' COMMENT '最后更新人ID',
    last_updater_name VARCHAR(50) NOT NULL DEFAULT '' COMMENT '最后更新人姓名',
    last_update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '最后更新时间',
    del_flag CHAR(1) NOT NULL DEFAULT '0' COMMENT '删除标志[0-否,1-是]',
    PRIMARY KEY (id),
    UNIQUE KEY uk_memory_write_receipt (user_id, scope_id, logical_key),
    CONSTRAINT fk_memory_receipt_scope FOREIGN KEY (user_id, scope_id) REFERENCES sq_execution_scope (user_id, scope_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='记忆工具的持久幂等收据';

CREATE TABLE sq_memory_source
(
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '存储主键ID',
    user_id CHAR(36) NOT NULL COMMENT '归属用户ID',
    memory_id CHAR(36) NOT NULL COMMENT '记忆业务ID',
    memory_version BIGINT NOT NULL COMMENT '显式授权时的记忆版本',
    source_message_id CHAR(36) NOT NULL COMMENT '用户显式授权原话来源ID，不复制正文',
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE' COMMENT '来源状态[ACTIVE,INVALIDATED]',
    creator_id VARCHAR(36) NOT NULL DEFAULT '' COMMENT '创建人ID',
    creator_name VARCHAR(50) NOT NULL DEFAULT '' COMMENT '创建人姓名',
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    last_updater_id VARCHAR(36) NOT NULL DEFAULT '' COMMENT '最后更新人ID',
    last_updater_name VARCHAR(50) NOT NULL DEFAULT '' COMMENT '最后更新人姓名',
    last_update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '最后更新时间',
    del_flag CHAR(1) NOT NULL DEFAULT '0' COMMENT '删除标志[0-否,1-是]',
    PRIMARY KEY (id),
    UNIQUE KEY uk_memory_source (user_id, memory_id, memory_version, source_message_id),
    KEY idx_memory_source_message (user_id, source_message_id, status),
    CONSTRAINT fk_memory_source_record FOREIGN KEY (user_id, memory_id) REFERENCES sq_memory (user_id, memory_id),
    CONSTRAINT ck_memory_source_status CHECK (status IN ('ACTIVE','INVALIDATED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='显式记忆原话来源和失效记录';
