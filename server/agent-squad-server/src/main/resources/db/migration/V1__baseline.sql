-- Agent Squad 数据库结构单一 baseline。
-- 由原 V1–V13 增量迁移合并而来（开发阶段，无生产迁移历史需保留）。
-- 业务表统一 7 字段审计口径；框架表 agentscope_* 由 Flyway 管理，不授权框架启动时建表。
-- 关键事务锁序：Conversation -> GoalTask -> Binding/Command/Event -> Operation/Interaction。

CREATE TABLE sq_conversation
(
    id                BIGINT       NOT NULL AUTO_INCREMENT COMMENT '存储主键ID',
    conversation_id   CHAR(36)     NOT NULL COMMENT '对话业务ID',
    user_id           CHAR(36)     NOT NULL COMMENT '资源归属用户ID',
    title             VARCHAR(120) NOT NULL COMMENT '对话标题',
    generation        BIGINT       NOT NULL DEFAULT 0 COMMENT '会话代数',
    status            VARCHAR(16)  NOT NULL COMMENT 'ACTIVE/DELETING/DELETED',
    active_task_id    CHAR(36) NULL COMMENT '未终结目标ID',
    version           BIGINT       NOT NULL DEFAULT 0 COMMENT '业务版本',
    creator_id        VARCHAR(36)  NOT NULL DEFAULT '' COMMENT '创建人ID',
    creator_name      VARCHAR(50)  NOT NULL DEFAULT '' COMMENT '创建人姓名',
    create_time       DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    last_updater_id   VARCHAR(36)  NOT NULL DEFAULT '' COMMENT '最后更新人ID',
    last_updater_name VARCHAR(50)  NOT NULL DEFAULT '' COMMENT '最后更新人姓名',
    last_update_time  DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '最后更新时间',
    del_flag          CHAR(1)      NOT NULL DEFAULT '0' COMMENT '删除标志[0-否,1-是]',
    PRIMARY KEY (id),
    UNIQUE KEY uk_owner_conversation (user_id, conversation_id),
    CONSTRAINT ck_conversation_del CHECK (del_flag IN ('0', '1'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='用户对话';

CREATE TABLE sq_goal_task
(
    id                         BIGINT       NOT NULL AUTO_INCREMENT COMMENT '存储主键ID',
    task_id                    CHAR(36)     NOT NULL COMMENT '目标业务ID',
    user_id                    CHAR(36)     NOT NULL COMMENT '资源归属用户ID',
    conversation_id            CHAR(36)     NOT NULL COMMENT '所属对话ID',
    status                     VARCHAR(24)  NOT NULL COMMENT '产品状态',
    version                    BIGINT       NOT NULL DEFAULT 0 COMMENT '业务版本',
    event_seq                  BIGINT       NOT NULL DEFAULT 0 COMMENT '公开事件水位',
    input_json                 JSON         NOT NULL COMMENT '规范化用户输入',
    plan_version               BIGINT       NOT NULL DEFAULT 0 COMMENT '计划版本',
    answer_revision            BIGINT       NOT NULL DEFAULT 0 COMMENT '用户可见最终答案版本',
    cancel_requested_at        DATETIME(3) NULL COMMENT '取消意图时间，完成裁决屏障',
    completion_proposal_json   JSON         NULL COMMENT 'Master 提交的完成候选，非最终结果',
    proposal_binding_id        CHAR(36)     NULL COMMENT '候选来源绑定ID',
    proposal_turn_id           VARCHAR(64)  NULL COMMENT '候选来源原生TurnID',
    proposal_tool_call_id      VARCHAR(128) NULL COMMENT '候选来源工具调用ID',
    proposal_hash              CHAR(64)     NULL COMMENT '候选内容摘要，用于重放校验',
    completion_correction_count INT         NOT NULL DEFAULT 0 COMMENT '完成候选缺失后的系统纠正次数',
    blocked_reason             VARCHAR(1000) NULL COMMENT '当前恢复阻塞原因；不保存原生异常正文',
    creator_id                 VARCHAR(36)  NOT NULL DEFAULT '' COMMENT '创建人ID',
    creator_name               VARCHAR(50)  NOT NULL DEFAULT '' COMMENT '创建人姓名',
    create_time                DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    last_updater_id            VARCHAR(36)  NOT NULL DEFAULT '' COMMENT '最后更新人ID',
    last_updater_name          VARCHAR(50)  NOT NULL DEFAULT '' COMMENT '最后更新人姓名',
    last_update_time           DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '最后更新时间',
    del_flag                   CHAR(1)      NOT NULL DEFAULT '0' COMMENT '删除标志[0-否,1-是]',
    PRIMARY KEY (id),
    UNIQUE KEY uk_owner_task (user_id, task_id),
    CONSTRAINT fk_task_conversation FOREIGN KEY (user_id, conversation_id)
        REFERENCES sq_conversation (user_id, conversation_id),
    CONSTRAINT ck_goal_task_del CHECK (del_flag IN ('0', '1'))
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
    native_event_cursor  BIGINT      NOT NULL DEFAULT 0 COMMENT '已投影的 AgentScope 日志序号',
    next_projection_at   DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '下次读取原生日志时间',
    creator_id           VARCHAR(36) NOT NULL DEFAULT '' COMMENT '创建人ID',
    creator_name         VARCHAR(50) NOT NULL DEFAULT '' COMMENT '创建人姓名',
    create_time          DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    last_updater_id      VARCHAR(36) NOT NULL DEFAULT '' COMMENT '最后更新人ID',
    last_updater_name    VARCHAR(50) NOT NULL DEFAULT '' COMMENT '最后更新人姓名',
    last_update_time     DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '最后更新时间',
    del_flag             CHAR(1)     NOT NULL DEFAULT '0' COMMENT '删除标志[0-否,1-是]',
    PRIMARY KEY (id),
    UNIQUE KEY uk_owner_binding (user_id, binding_id),
    CONSTRAINT fk_binding_task FOREIGN KEY (user_id, task_id)
        REFERENCES sq_goal_task (user_id, task_id),
    CONSTRAINT ck_runtime_binding_del CHECK (del_flag IN ('0', '1'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='AgentScope会话绑定';

CREATE TABLE sq_command
(
    id                    BIGINT       NOT NULL AUTO_INCREMENT COMMENT '存储主键ID',
    command_id            CHAR(36)     NOT NULL COMMENT '命令业务ID',
    user_id               CHAR(36)     NOT NULL COMMENT '资源归属用户ID',
    scope_key             VARCHAR(160) NOT NULL COMMENT '动作和资源幂等范围',
    idempotency_key       VARCHAR(160) NOT NULL COMMENT '调用方稳定键',
    payload_hash          CHAR(64)     NOT NULL COMMENT '规范化请求SHA256',
    task_id               CHAR(36) NULL COMMENT '所属目标ID；删除类命令可为空',
    binding_id            CHAR(36) NULL COMMENT '原生会话绑定ID；删除类命令可为空',
    action                VARCHAR(32)  NOT NULL COMMENT '命令动作',
    payload_json          JSON         NOT NULL COMMENT '规范化命令输入',
    status                VARCHAR(20)  NOT NULL COMMENT 'PENDING/DELIVERING/DELIVERED/APPLIED/BLOCKED',
    due_at                DATETIME(3) NOT NULL COMMENT '下次投递时间',
    delivery_token        CHAR(36) NULL COMMENT '当前投递领取令牌',
    delivery_lease_until  DATETIME(3) NULL COMMENT '投递令牌过期时间',
    delivery_attempts     INT          NOT NULL DEFAULT 0 COMMENT '投递尝试次数',
    native_turn_id        VARCHAR(64) NULL COMMENT 'AgentScope 原生回合ID',
    native_run_id         VARCHAR(64) NULL COMMENT 'AgentScope 原生运行ID',
    native_status         VARCHAR(24) NULL COMMENT 'AgentScope 提交时状态',
    last_error_code       VARCHAR(64) NULL COMMENT '投递错误分类，不存异常详情',
    creator_id            VARCHAR(36) NOT NULL DEFAULT '' COMMENT '创建人ID',
    creator_name          VARCHAR(50) NOT NULL DEFAULT '' COMMENT '创建人姓名',
    create_time           DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    last_updater_id       VARCHAR(36) NOT NULL DEFAULT '' COMMENT '最后更新人ID',
    last_updater_name     VARCHAR(50) NOT NULL DEFAULT '' COMMENT '最后更新人姓名',
    last_update_time      DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '最后更新时间',
    del_flag              CHAR(1)     NOT NULL DEFAULT '0' COMMENT '删除标志[0-否,1-是]',
    PRIMARY KEY (id),
    UNIQUE KEY uk_command (command_id),
    UNIQUE KEY uk_owner_scope_key (user_id, scope_key, idempotency_key),
    KEY             idx_command_due (status, due_at),
    CONSTRAINT fk_command_task FOREIGN KEY (user_id, task_id)
        REFERENCES sq_goal_task (user_id, task_id),
    CONSTRAINT ck_command_del CHECK (del_flag IN ('0', '1'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='持久命令与幂等回执';

CREATE TABLE sq_task_event
(
    id                BIGINT       NOT NULL AUTO_INCREMENT COMMENT '存储主键ID',
    user_id           CHAR(36)     NOT NULL COMMENT '资源归属用户ID',
    task_id           CHAR(36)     NOT NULL COMMENT '目标ID',
    event_seq         BIGINT       NOT NULL COMMENT '目标内事件序号',
    producer_key      VARCHAR(200) NOT NULL COMMENT '稳定生产事件键',
    event_type        VARCHAR(64)  NOT NULL COMMENT '公开事件类型',
    payload_json      JSON         NOT NULL COMMENT '公开事件载荷',
    occurred_at       DATETIME(3) NOT NULL COMMENT '发生时间',
    creator_id        VARCHAR(36)  NOT NULL DEFAULT '' COMMENT '创建人ID',
    creator_name      VARCHAR(50)  NOT NULL DEFAULT '' COMMENT '创建人姓名',
    create_time       DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    last_updater_id   VARCHAR(36)  NOT NULL DEFAULT '' COMMENT '最后更新人ID',
    last_updater_name VARCHAR(50)  NOT NULL DEFAULT '' COMMENT '最后更新人姓名',
    last_update_time  DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '最后更新时间',
    del_flag          CHAR(1)      NOT NULL DEFAULT '0' COMMENT '删除标志[0-否,1-是]',
    PRIMARY KEY (id),
    UNIQUE KEY uk_task_seq (user_id, task_id, event_seq),
    UNIQUE KEY uk_producer (user_id, task_id, producer_key),
    CONSTRAINT fk_event_task FOREIGN KEY (user_id, task_id)
        REFERENCES sq_goal_task (user_id, task_id),
    CONSTRAINT ck_task_event_del CHECK (del_flag IN ('0', '1'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='用户可见持久事件';

CREATE TABLE sq_connection
(
    id                     BIGINT       NOT NULL AUTO_INCREMENT COMMENT '存储主键ID',
    connection_id          CHAR(36)     NOT NULL COMMENT '连接业务ID',
    user_id                CHAR(36)     NOT NULL COMMENT '资源归属用户ID',
    name                   VARCHAR(100) NOT NULL COMMENT '显示名称',
    provider               VARCHAR(80)  NOT NULL COMMENT '提供方编码',
    environment            VARCHAR(80)  NOT NULL COMMENT '环境标识',
    resource_policy_json   JSON         NOT NULL COMMENT '受控资源范围',
    secret_ref             VARCHAR(240) NOT NULL COMMENT '密钥管理引用；不含明文',
    create_idempotency_key VARCHAR(160) NULL COMMENT '创建幂等键；同键同用户复用原连接',
    revision               BIGINT       NOT NULL DEFAULT 0 COMMENT '配置授权版本',
    enabled                TINYINT      NOT NULL DEFAULT 1 COMMENT '是否启用[0/1]',
    creator_id             VARCHAR(36)  NOT NULL DEFAULT '' COMMENT '创建人ID',
    creator_name           VARCHAR(50)  NOT NULL DEFAULT '' COMMENT '创建人姓名',
    create_time            DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    last_updater_id        VARCHAR(36)  NOT NULL DEFAULT '' COMMENT '最后更新人ID',
    last_updater_name      VARCHAR(50)  NOT NULL DEFAULT '' COMMENT '最后更新人姓名',
    last_update_time       DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '最后更新时间',
    del_flag               CHAR(1)      NOT NULL DEFAULT '0' COMMENT '删除标志[0-否,1-是]',
    PRIMARY KEY (id),
    UNIQUE KEY uk_owner_connection (user_id, connection_id),
    UNIQUE KEY uk_owner_create_idem (user_id, create_idempotency_key),
    CONSTRAINT ck_connection_del CHECK (del_flag IN ('0', '1'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='用户外部连接凭据引用';

CREATE TABLE sq_secret_vault
(
    id               BIGINT        NOT NULL AUTO_INCREMENT COMMENT '存储主键ID',
    secret_ref       VARCHAR(240)  NOT NULL COMMENT '密钥引用；格式 local-vault:keyId:uuid',
    key_id           VARCHAR(64)   NOT NULL COMMENT '加密密钥版本ID；轮换期间新旧并存',
    cipher_bytes     VARBINARY(2048) NOT NULL COMMENT 'AES-GCM 密文；IV 前置',
    creator_id       VARCHAR(36)   NOT NULL DEFAULT '' COMMENT '创建人ID',
    creator_name     VARCHAR(50)   NOT NULL DEFAULT '' COMMENT '创建人姓名',
    create_time      DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    last_updater_id  VARCHAR(36)   NOT NULL DEFAULT '' COMMENT '最后更新人ID',
    last_updater_name VARCHAR(50)   NOT NULL DEFAULT '' COMMENT '最后更新人姓名',
    last_update_time DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '最后更新时间',
    del_flag         CHAR(1)       NOT NULL DEFAULT '0' COMMENT '删除标志[0-否,1-是]',
    PRIMARY KEY (id),
    UNIQUE KEY uk_secret_ref (secret_ref),
    CONSTRAINT ck_secret_vault_del CHECK (del_flag IN ('0', '1'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='本地密钥保险库密文；不保存明文';

CREATE TABLE sq_operation
(
    id                  BIGINT       NOT NULL AUTO_INCREMENT COMMENT '存储主键ID',
    operation_id        CHAR(36)     NOT NULL COMMENT '操作ID',
    user_id             CHAR(36)     NOT NULL COMMENT '资源归属用户ID',
    task_id             CHAR(36)     NOT NULL COMMENT '所属目标ID',
    job_id              CHAR(36) NULL COMMENT '节点ID；Master可空',
    binding_id          CHAR(36)     NOT NULL COMMENT '原生调用所属绑定',
    framework_turn_id   VARCHAR(200) NOT NULL COMMENT '原生TurnID',
    tool_call_id        VARCHAR(200) NOT NULL COMMENT '原生稳定工具调用ID',
    tool_key            VARCHAR(100) NOT NULL COMMENT '工具键',
    tool_version        VARCHAR(40)  NOT NULL COMMENT '工具版本',
    business_intent_key VARCHAR(160) NULL COMMENT '业务写入幂等键；READ可空',
    effect              VARCHAR(16)  NOT NULL COMMENT 'READ/WRITE/SANDBOX',
    connection_id       CHAR(36) NULL COMMENT '用户连接ID',
    connection_revision BIGINT       NOT NULL DEFAULT 0 COMMENT '派发时连接版本',
    auth_version        BIGINT       NOT NULL DEFAULT 0 COMMENT '派发时授权版本',
    request_hash        VARCHAR(64)  NOT NULL COMMENT '规范化参数摘要',
    arguments_json      JSON         NOT NULL COMMENT '脱敏参数；敏感值用引用',
    status              VARCHAR(24)  NOT NULL COMMENT 'PREPARED/WAITING_APPROVAL/IN_FLIGHT/UNKNOWN/RECONCILING/SUCCEEDED/FAILED/CANCELLED',
    version             BIGINT       NOT NULL DEFAULT 0 COMMENT '业务乐观版本',
    executor_owner      VARCHAR(100) NULL COMMENT '独立执行器实例',
    executor_fence      BIGINT       NOT NULL DEFAULT 0 COMMENT '独立执行器栅栏',
    executor_until      DATETIME(3)  NULL COMMENT '执行器租约',
    external_key        VARCHAR(200) NULL COMMENT '外部幂等或查询关联号',
    result_json         JSON NULL COMMENT '可信连接器结果或核对事实',
    creator_id          VARCHAR(36)  NOT NULL DEFAULT '' COMMENT '创建人ID',
    creator_name        VARCHAR(50)  NOT NULL DEFAULT '' COMMENT '创建人姓名',
    create_time         DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    last_updater_id     VARCHAR(36)  NOT NULL DEFAULT '' COMMENT '最后更新人ID',
    last_updater_name   VARCHAR(50)  NOT NULL DEFAULT '' COMMENT '最后更新人姓名',
    last_update_time    DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '最后更新时间',
    del_flag            CHAR(1)      NOT NULL DEFAULT '0' COMMENT '删除标志[0-否,1-是]',
    PRIMARY KEY (id),
    UNIQUE KEY uk_owner_operation (user_id, operation_id),
    UNIQUE KEY uk_native_call (user_id, binding_id, framework_turn_id, tool_call_id),
    UNIQUE KEY uk_business_intent (user_id, task_id, tool_key, business_intent_key),
    KEY                 idx_operation_unknown (status, last_update_time),
    CONSTRAINT ck_operation_del CHECK (del_flag IN ('0', '1'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='外部工具副作用账本';

CREATE TABLE sq_budget_reservation
(
    id                BIGINT       NOT NULL AUTO_INCREMENT COMMENT '存储主键ID',
    reservation_id    CHAR(36)     NOT NULL COMMENT '预留ID',
    user_id           CHAR(36)     NOT NULL COMMENT '资源归属用户ID',
    task_id           CHAR(36)     NOT NULL COMMENT '目标ID',
    usage_key         VARCHAR(200) NOT NULL COMMENT '模型或工具真实请求稳定键',
    kind              VARCHAR(16)  NOT NULL COMMENT 'MODEL/TOOL',
    reserved_units    BIGINT       NOT NULL DEFAULT 0 COMMENT '预留整数微单位',
    settled_units     BIGINT       NOT NULL DEFAULT 0 COMMENT '已结算整数微单位',
    input_tokens      BIGINT       NOT NULL DEFAULT 0 COMMENT '输入token累计',
    output_tokens     BIGINT       NOT NULL DEFAULT 0 COMMENT '输出token累计',
    status            VARCHAR(16)  NOT NULL COMMENT 'RESERVED/SETTLED/UNKNOWN',
    version           BIGINT       NOT NULL DEFAULT 0 COMMENT '结算版本',
    creator_id        VARCHAR(36)  NOT NULL DEFAULT '' COMMENT '创建人ID',
    creator_name      VARCHAR(50)  NOT NULL DEFAULT '' COMMENT '创建人姓名',
    create_time       DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    last_updater_id   VARCHAR(36)  NOT NULL DEFAULT '' COMMENT '最后更新人ID',
    last_updater_name VARCHAR(50)  NOT NULL DEFAULT '' COMMENT '最后更新人姓名',
    last_update_time  DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '最后更新时间',
    del_flag          CHAR(1)      NOT NULL DEFAULT '0' COMMENT '删除标志[0-否,1-是]',
    PRIMARY KEY (id),
    UNIQUE KEY uk_reservation (reservation_id),
    UNIQUE KEY uk_usage (user_id, task_id, usage_key),
    CONSTRAINT ck_budget_reservation_del CHECK (del_flag IN ('0', '1'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='全局预算预留及可信结算';

CREATE TABLE sq_mock_ticket
(
    id               BIGINT        NOT NULL AUTO_INCREMENT COMMENT '存储主键ID',
    user_id          CHAR(36)      NOT NULL COMMENT '资源归属用户ID',
    external_key     VARCHAR(200)  NOT NULL COMMENT '外部幂等键；同键同内容复用原单',
    request_hash     VARCHAR(64)   NOT NULL COMMENT '请求参数摘要',
    title            VARCHAR(200)  NOT NULL COMMENT '工单标题',
    detail           VARCHAR(2000) NOT NULL COMMENT '工单描述',
    creator_id       VARCHAR(36)   NOT NULL DEFAULT '' COMMENT '创建人ID',
    creator_name     VARCHAR(50)   NOT NULL DEFAULT '' COMMENT '创建人姓名',
    create_time      DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    last_updater_id  VARCHAR(36)   NOT NULL DEFAULT '' COMMENT '最后更新人ID',
    last_updater_name VARCHAR(50)   NOT NULL DEFAULT '' COMMENT '最后更新人姓名',
    last_update_time DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '最后更新时间',
    del_flag         CHAR(1)       NOT NULL DEFAULT '0' COMMENT '删除标志[0-否,1-是]',
    PRIMARY KEY (id),
    UNIQUE KEY uk_mock_ticket_key (user_id, external_key),
    CONSTRAINT ck_mock_ticket_del CHECK (del_flag IN ('0', '1'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='本地模拟外部工单系统';

CREATE TABLE sq_interaction
(
    id                   BIGINT        NOT NULL AUTO_INCREMENT COMMENT '存储主键ID',
    interaction_id       CHAR(36)      NOT NULL COMMENT '交互ID',
    user_id              CHAR(36)      NOT NULL COMMENT '资源归属用户ID',
    task_id              CHAR(36)      NOT NULL COMMENT '目标ID',
    job_id               CHAR(36) NULL COMMENT '节点ID',
    binding_id           CHAR(36) NULL COMMENT '所属绑定',
    operation_id         CHAR(36) NULL COMMENT '关联操作',
    origin               VARCHAR(16)   NOT NULL COMMENT 'BUSINESS/FRAMEWORK',
    framework_request_id VARCHAR(200) NULL COMMENT '真实原生请求ID；业务交互为空',
    kind                 VARCHAR(16)   NOT NULL COMMENT 'INPUT/APPROVAL',
    question             VARCHAR(4000) NOT NULL COMMENT '用户问题',
    answer_schema_json   JSON          NOT NULL COMMENT '回复JSON Schema',
    plan_version         BIGINT        NOT NULL DEFAULT 0 COMMENT '绑定计划版本',
    request_hash         VARCHAR(64)   NOT NULL COMMENT '绑定请求摘要',
    auth_version         BIGINT        NOT NULL DEFAULT 0 COMMENT '确认授权版本',
    connection_revision  BIGINT        NOT NULL DEFAULT 0 COMMENT '确认连接版本',
    expires_at           DATETIME(3)  NOT NULL COMMENT '答复有效期',
    status               VARCHAR(16)   NOT NULL COMMENT 'OPEN/RESPONDED/EXPIRED/INVALIDATED/CANCELLED',
    version              BIGINT        NOT NULL DEFAULT 0 COMMENT '乐观版本',
    response_json        JSON NULL COMMENT '受Schema校验的用户回复',
    creator_id           VARCHAR(36)   NOT NULL DEFAULT '' COMMENT '创建人ID',
    creator_name         VARCHAR(50)   NOT NULL DEFAULT '' COMMENT '创建人姓名',
    create_time          DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    last_updater_id      VARCHAR(36)   NOT NULL DEFAULT '' COMMENT '最后更新人ID',
    last_updater_name    VARCHAR(50)   NOT NULL DEFAULT '' COMMENT '最后更新人姓名',
    last_update_time     DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '最后更新时间',
    del_flag             CHAR(1)       NOT NULL DEFAULT '0' COMMENT '删除标志[0-否,1-是]',
    PRIMARY KEY (id),
    UNIQUE KEY uk_owner_interaction (user_id, interaction_id),
    UNIQUE KEY uk_framework_request (user_id, binding_id, framework_request_id),
    CONSTRAINT ck_interaction_del CHECK (del_flag IN ('0', '1'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='持久输入与审批请求';

CREATE TABLE sq_worker_definition
(
    id                BIGINT       NOT NULL AUTO_INCREMENT COMMENT '存储主键ID',
    definition_id     CHAR(36)     NOT NULL COMMENT '能力定义业务ID',
    worker_key        VARCHAR(80)  NOT NULL COMMENT '稳定能力键',
    worker_version    VARCHAR(40)  NOT NULL COMMENT '不可变能力版本',
    status            VARCHAR(16)  NOT NULL COMMENT '状态[ENABLED,DISABLED,REVOKED]',
    version           BIGINT       NOT NULL DEFAULT 0 COMMENT '状态乐观版本',
    definition_json   JSON         NOT NULL COMMENT '不可变能力定义',
    publish_key       VARCHAR(160) NOT NULL COMMENT '发布幂等键',
    payload_hash      CHAR(64)     NOT NULL COMMENT '发布内容SHA256摘要',
    last_status_key   VARCHAR(160) NULL COMMENT '最近一次状态修改幂等键',
    last_status_actor VARCHAR(36) NULL COMMENT '最近一次状态修改主体ID',
    last_status_hash  CHAR(64) NULL COMMENT '最近一次状态修改内容摘要',
    creator_id        VARCHAR(36)  NOT NULL DEFAULT '' COMMENT '创建人ID',
    creator_name      VARCHAR(50)  NOT NULL DEFAULT '' COMMENT '创建人姓名',
    create_time       DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    last_updater_id   VARCHAR(36)  NOT NULL DEFAULT '' COMMENT '最后更新人ID',
    last_updater_name VARCHAR(50)  NOT NULL DEFAULT '' COMMENT '最后更新人姓名',
    last_update_time  DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '最后更新时间',
    del_flag          CHAR(1)      NOT NULL DEFAULT '0' COMMENT '删除标志[0-否,1-是]',
    PRIMARY KEY (id),
    UNIQUE KEY uk_worker_definition_id (definition_id),
    UNIQUE KEY uk_worker_definition_version (worker_key, worker_version),
    UNIQUE KEY uk_worker_definition_publish (creator_id, publish_key),
    KEY               idx_worker_definition_status (status, id),
    CONSTRAINT ck_worker_definition_status CHECK (status IN ('ENABLED', 'DISABLED', 'REVOKED')),
    CONSTRAINT ck_worker_definition_del CHECK (del_flag IN ('0', '1'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='不可变Worker能力定义';

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
