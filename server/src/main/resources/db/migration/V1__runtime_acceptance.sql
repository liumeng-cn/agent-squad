-- D1 运行受理最小表。后续模块按数据设计逐次迁移，禁止运行时自动建表。
CREATE TABLE sq_conversation
(
    id               BIGINT       NOT NULL AUTO_INCREMENT COMMENT '存储主键ID',
    conversation_id  CHAR(36)     NOT NULL COMMENT '对话业务ID',
    user_id          CHAR(36)     NOT NULL COMMENT '资源归属用户ID',
    title            VARCHAR(120) NOT NULL COMMENT '对话标题',
    generation       BIGINT       NOT NULL DEFAULT 0 COMMENT '会话代数',
    status           VARCHAR(16)  NOT NULL COMMENT 'ACTIVE/DELETING/DELETED',
    active_task_id   CHAR(36) NULL COMMENT '未终结目标ID',
    version          BIGINT       NOT NULL DEFAULT 0 COMMENT '业务版本',
    create_time      DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    last_update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '最后更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_owner_conversation (user_id, conversation_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='用户对话';

CREATE TABLE sq_goal_task
(
    id               BIGINT      NOT NULL AUTO_INCREMENT COMMENT '存储主键ID',
    task_id          CHAR(36)    NOT NULL COMMENT '目标业务ID',
    user_id          CHAR(36)    NOT NULL COMMENT '资源归属用户ID',
    conversation_id  CHAR(36)    NOT NULL COMMENT '所属对话ID',
    status           VARCHAR(24) NOT NULL COMMENT '产品状态',
    version          BIGINT      NOT NULL DEFAULT 0 COMMENT '业务版本',
    event_seq        BIGINT      NOT NULL DEFAULT 0 COMMENT '公开事件水位',
    input_json       JSON        NOT NULL COMMENT '规范化用户输入',
    create_time      DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    last_update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '最后更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_owner_task (user_id, task_id),
    CONSTRAINT fk_task_conversation FOREIGN KEY (user_id, conversation_id)
        REFERENCES sq_conversation (user_id, conversation_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='用户目标';

CREATE TABLE sq_runtime_binding
(
    id                   BIGINT      NOT NULL AUTO_INCREMENT COMMENT '存储主键ID',
    binding_id           CHAR(36)    NOT NULL COMMENT '绑定业务ID',
    user_id              CHAR(36)    NOT NULL COMMENT '资源归属用户ID',
    task_id              CHAR(36)    NOT NULL COMMENT '所属目标ID',
    role                 VARCHAR(16) NOT NULL COMMENT 'MASTER/WORKER',
    agent_id             CHAR(32)    NOT NULL COMMENT '固定能力版本标识',
    framework_session_id CHAR(36)    NOT NULL COMMENT '原生会话ID',
    status               VARCHAR(24) NOT NULL COMMENT '生命周期状态',
    create_time          DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_owner_binding (user_id, binding_id),
    CONSTRAINT fk_binding_task FOREIGN KEY (user_id, task_id)
        REFERENCES sq_goal_task (user_id, task_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='AgentScope会话绑定';

CREATE TABLE sq_command
(
    id              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '存储主键ID',
    command_id      CHAR(36)     NOT NULL COMMENT '命令业务ID',
    user_id         CHAR(36)     NOT NULL COMMENT '资源归属用户ID',
    scope_key       VARCHAR(160) NOT NULL COMMENT '动作和资源幂等范围',
    idempotency_key VARCHAR(160) NOT NULL COMMENT '调用方稳定键',
    payload_hash    CHAR(64)     NOT NULL COMMENT '规范化请求SHA256',
    task_id         CHAR(36)     NOT NULL COMMENT '所属目标ID',
    binding_id      CHAR(36)     NOT NULL COMMENT '原生会话绑定ID',
    action          VARCHAR(32)  NOT NULL COMMENT '命令动作',
    payload_json    JSON         NOT NULL COMMENT '规范化命令输入',
    status          VARCHAR(20)  NOT NULL COMMENT 'PENDING/DELIVERING/DELIVERED/APPLIED/BLOCKED',
    due_at          DATETIME(3) NOT NULL COMMENT '下次投递时间',
    create_time     DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_command (command_id),
    UNIQUE KEY uk_owner_scope_key (user_id, scope_key, idempotency_key),
    KEY             idx_command_due (status, due_at),
    CONSTRAINT fk_command_task FOREIGN KEY (user_id, task_id)
        REFERENCES sq_goal_task (user_id, task_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='持久命令与幂等回执';

CREATE TABLE sq_task_event
(
    id           BIGINT       NOT NULL AUTO_INCREMENT COMMENT '存储主键ID',
    user_id      CHAR(36)     NOT NULL COMMENT '资源归属用户ID',
    task_id      CHAR(36)     NOT NULL COMMENT '目标ID',
    event_seq    BIGINT       NOT NULL COMMENT '目标内事件序号',
    producer_key VARCHAR(200) NOT NULL COMMENT '稳定生产事件键',
    event_type   VARCHAR(64)  NOT NULL COMMENT '公开事件类型',
    payload_json JSON         NOT NULL COMMENT '公开事件载荷',
    occurred_at  DATETIME(3) NOT NULL COMMENT '发生时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_task_seq (user_id, task_id, event_seq),
    UNIQUE KEY uk_producer (user_id, task_id, producer_key),
    CONSTRAINT fk_event_task FOREIGN KEY (user_id, task_id)
        REFERENCES sq_goal_task (user_id, task_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='用户可见持久事件';
