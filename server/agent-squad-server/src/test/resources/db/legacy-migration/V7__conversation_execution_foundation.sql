-- 扩展消息与执行归属；不修改旧 Session/Command 身份，不清理旧记录。
-- 归属列暂可空，以便先扩展、事务回填，再切换运行入口；后续服务必须校验有效 Scope。
ALTER TABLE sq_conversation ADD COLUMN message_seq BIGINT NOT NULL DEFAULT 0 COMMENT '已分配的会话消息序号';
ALTER TABLE sq_conversation ADD COLUMN conversation_event_seq BIGINT NOT NULL DEFAULT 0 COMMENT '会话公开事件水位';
ALTER TABLE sq_conversation ADD COLUMN title_source VARCHAR(20) NOT NULL DEFAULT 'LEGACY' COMMENT '标题来源[LEGACY,FALLBACK,AUTO_PENDING,AUTO,MANUAL]';
ALTER TABLE sq_conversation ADD COLUMN title_version BIGINT NOT NULL DEFAULT 0 COMMENT '标题独立乐观版本';
ALTER TABLE sq_conversation ADD COLUMN last_activity_at DATETIME(3) NULL COMMENT '最后用户交流或助手交付时间';

CREATE TABLE sq_budget_account
(
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '存储主键ID',
    account_id CHAR(36) NOT NULL COMMENT '预算账户业务ID',
    user_id CHAR(36) NOT NULL COMMENT '资源归属用户ID',
    limit_microunits BIGINT NOT NULL COMMENT '固定共享预算上限，整数微单位',
    profile_key VARCHAR(80) NOT NULL COMMENT '固定预算Profile键',
    status VARCHAR(16) NOT NULL COMMENT '状态[OPEN,CLOSED]',
    version BIGINT NOT NULL DEFAULT 0 COMMENT '业务乐观版本',
    creator_id VARCHAR(36) NOT NULL DEFAULT '' COMMENT '创建人ID',
    creator_name VARCHAR(50) NOT NULL DEFAULT '' COMMENT '创建人姓名',
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    last_updater_id VARCHAR(36) NOT NULL DEFAULT '' COMMENT '最后更新人ID',
    last_updater_name VARCHAR(50) NOT NULL DEFAULT '' COMMENT '最后更新人姓名',
    last_update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '最后更新时间',
    del_flag CHAR(1) NOT NULL DEFAULT '0' COMMENT '删除标志[0-否,1-是]',
    PRIMARY KEY (id),
    UNIQUE KEY uk_budget_account (user_id, account_id),
    CONSTRAINT ck_budget_account_limit CHECK (limit_microunits > 0),
    CONSTRAINT ck_budget_account_status CHECK (status IN ('OPEN', 'CLOSED')),
    CONSTRAINT ck_budget_account_del CHECK (del_flag IN ('0', '1'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='回复与任务共享预算账户';

CREATE TABLE sq_execution_scope
(
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '存储主键ID',
    scope_id CHAR(36) NOT NULL COMMENT '执行作用域业务ID',
    user_id CHAR(36) NOT NULL COMMENT '资源归属用户ID',
    conversation_id CHAR(36) NOT NULL COMMENT '所属会话ID',
    conversation_generation BIGINT NOT NULL COMMENT '受理时会话代际',
    kind VARCHAR(16) NOT NULL COMMENT '归属类型[RESPONSE,TASK]',
    owner_id CHAR(36) NOT NULL COMMENT '回复执行或目标业务ID',
    account_id CHAR(36) NOT NULL COMMENT '共享预算账户ID',
    creator_id VARCHAR(36) NOT NULL DEFAULT '' COMMENT '创建人ID',
    creator_name VARCHAR(50) NOT NULL DEFAULT '' COMMENT '创建人姓名',
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    last_updater_id VARCHAR(36) NOT NULL DEFAULT '' COMMENT '最后更新人ID',
    last_updater_name VARCHAR(50) NOT NULL DEFAULT '' COMMENT '最后更新人姓名',
    last_update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '最后更新时间',
    del_flag CHAR(1) NOT NULL DEFAULT '0' COMMENT '删除标志[0-否,1-是]',
    PRIMARY KEY (id),
    UNIQUE KEY uk_execution_scope (user_id, scope_id),
    UNIQUE KEY uk_scope_owner (user_id, kind, owner_id),
    CONSTRAINT fk_scope_conversation FOREIGN KEY (user_id, conversation_id) REFERENCES sq_conversation (user_id, conversation_id),
    CONSTRAINT fk_scope_account FOREIGN KEY (user_id, account_id) REFERENCES sq_budget_account (user_id, account_id),
    CONSTRAINT ck_scope_kind CHECK (kind IN ('RESPONSE', 'TASK')),
    CONSTRAINT ck_execution_scope_del CHECK (del_flag IN ('0', '1'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='统一执行归属与授权边界';

CREATE TABLE sq_message
(
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '存储主键ID',
    message_id CHAR(36) NOT NULL COMMENT '消息业务ID',
    user_id CHAR(36) NOT NULL COMMENT '资源归属用户ID',
    conversation_id CHAR(36) NOT NULL COMMENT '所属会话ID',
    message_seq BIGINT NOT NULL COMMENT '会话内递增消息序号',
    role VARCHAR(16) NOT NULL COMMENT '消息角色[USER,ASSISTANT]',
    content LONGTEXT NOT NULL COMMENT '用户消息或授权助手正文',
    revision BIGINT NOT NULL DEFAULT 0 COMMENT '正文替换版本',
    completed BOOLEAN NOT NULL DEFAULT FALSE COMMENT '正文是否完成',
    source_message_id CHAR(36) NULL COMMENT '回复所关联的用户消息ID',
    execution_id CHAR(36) NULL COMMENT '回复执行ID；旧任务消息为空',
    task_id CHAR(36) NULL COMMENT '可选关联目标ID',
    occurred_at DATETIME(3) NOT NULL COMMENT '消息发生时间',
    legacy_key VARCHAR(100) NULL COMMENT '旧记录回填稳定键；新消息为空',
    creator_id VARCHAR(36) NOT NULL DEFAULT '' COMMENT '创建人ID',
    creator_name VARCHAR(50) NOT NULL DEFAULT '' COMMENT '创建人姓名',
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    last_updater_id VARCHAR(36) NOT NULL DEFAULT '' COMMENT '最后更新人ID',
    last_updater_name VARCHAR(50) NOT NULL DEFAULT '' COMMENT '最后更新人姓名',
    last_update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '最后更新时间',
    del_flag CHAR(1) NOT NULL DEFAULT '0' COMMENT '删除标志[0-否,1-是]',
    PRIMARY KEY (id),
    UNIQUE KEY uk_message (user_id, message_id),
    UNIQUE KEY uk_conversation_message_seq (user_id, conversation_id, message_seq),
    UNIQUE KEY uk_message_legacy (user_id, legacy_key),
    CONSTRAINT fk_message_conversation FOREIGN KEY (user_id, conversation_id) REFERENCES sq_conversation (user_id, conversation_id),
    CONSTRAINT fk_message_task FOREIGN KEY (user_id, task_id) REFERENCES sq_goal_task (user_id, task_id),
    CONSTRAINT ck_message_role CHECK (role IN ('USER', 'ASSISTANT')),
    CONSTRAINT ck_message_seq CHECK (message_seq > 0 AND revision >= 0),
    CONSTRAINT ck_message_del CHECK (del_flag IN ('0', '1'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='持久用户与助手消息账本';

CREATE TABLE sq_response_execution
(
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '存储主键ID',
    execution_id CHAR(36) NOT NULL COMMENT '回复执行业务ID',
    user_id CHAR(36) NOT NULL COMMENT '资源归属用户ID',
    conversation_id CHAR(36) NOT NULL COMMENT '所属会话ID',
    source_message_id CHAR(36) NOT NULL COMMENT '来源用户消息ID',
    scope_id CHAR(36) NOT NULL COMMENT '回复执行作用域ID',
    binding_id CHAR(36) NOT NULL COMMENT '固定原生绑定ID',
    status VARCHAR(24) NOT NULL COMMENT '回复生命周期状态',
    version BIGINT NOT NULL DEFAULT 0 COMMENT '业务乐观版本',
    answer_message_id CHAR(36) NULL COMMENT '授权助手回答消息ID',
    cancel_requested_at DATETIME(3) NULL COMMENT '停止回答意图时间',
    completion_proposal_json JSON NULL COMMENT '待原生成功后裁决的完成候选',
    proposal_turn_id VARCHAR(64) NULL COMMENT '完成候选来源原生TurnID',
    proposal_hash CHAR(64) NULL COMMENT '完成候选规范化摘要',
    correction_count INT NOT NULL DEFAULT 0 COMMENT '无效完成候选纠正次数',
    recovery_attempts INT NOT NULL DEFAULT 0 COMMENT '已持久化的自动恢复次数',
    next_attempt_at DATETIME(3) NULL COMMENT '下次自动恢复时间',
    failure_id CHAR(36) NULL COMMENT '最近结构化故障ID',
    retry_of_execution_id CHAR(36) NULL COMMENT '终态重试的前序执行ID',
    creator_id VARCHAR(36) NOT NULL DEFAULT '' COMMENT '创建人ID',
    creator_name VARCHAR(50) NOT NULL DEFAULT '' COMMENT '创建人姓名',
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    last_updater_id VARCHAR(36) NOT NULL DEFAULT '' COMMENT '最后更新人ID',
    last_updater_name VARCHAR(50) NOT NULL DEFAULT '' COMMENT '最后更新人姓名',
    last_update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '最后更新时间',
    del_flag CHAR(1) NOT NULL DEFAULT '0' COMMENT '删除标志[0-否,1-是]',
    PRIMARY KEY (id),
    UNIQUE KEY uk_response_execution (user_id, execution_id),
    UNIQUE KEY uk_response_scope (user_id, scope_id),
    UNIQUE KEY uk_response_binding (user_id, binding_id),
    KEY idx_response_conversation (user_id, conversation_id, id),
    CONSTRAINT fk_response_message FOREIGN KEY (user_id, source_message_id) REFERENCES sq_message (user_id, message_id),
    CONSTRAINT fk_response_conversation FOREIGN KEY (user_id, conversation_id) REFERENCES sq_conversation (user_id, conversation_id),
    CONSTRAINT fk_response_scope FOREIGN KEY (user_id, scope_id) REFERENCES sq_execution_scope (user_id, scope_id),
    CONSTRAINT ck_response_status CHECK (status IN ('QUEUED', 'RUNNING', 'WAITING_INPUT', 'WAITING_APPROVAL', 'RECOVERING', 'RECONCILING', 'CANCELLING', 'SUCCEEDED', 'PARTIAL', 'FAILED', 'CANCELLED')),
    CONSTRAINT ck_response_del CHECK (del_flag IN ('0', '1'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='独立于目标任务的可靠回复执行';

CREATE TABLE sq_conversation_event
(
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '存储主键ID',
    user_id CHAR(36) NOT NULL COMMENT '资源归属用户ID',
    conversation_id CHAR(36) NOT NULL COMMENT '所属会话ID',
    generation BIGINT NOT NULL COMMENT '事件所属会话代际',
    event_seq BIGINT NOT NULL COMMENT '会话内递增公开事件序号',
    producer_key VARCHAR(200) NOT NULL COMMENT '稳定生产事件键',
    event_type VARCHAR(64) NOT NULL COMMENT '公开事件类型',
    payload_json JSON NOT NULL COMMENT '脱敏公开事件载荷',
    occurred_at DATETIME(3) NOT NULL COMMENT '发生时间',
    creator_id VARCHAR(36) NOT NULL DEFAULT '' COMMENT '创建人ID',
    creator_name VARCHAR(50) NOT NULL DEFAULT '' COMMENT '创建人姓名',
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    last_updater_id VARCHAR(36) NOT NULL DEFAULT '' COMMENT '最后更新人ID',
    last_updater_name VARCHAR(50) NOT NULL DEFAULT '' COMMENT '最后更新人姓名',
    last_update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '最后更新时间',
    del_flag CHAR(1) NOT NULL DEFAULT '0' COMMENT '删除标志[0-否,1-是]',
    PRIMARY KEY (id),
    UNIQUE KEY uk_conversation_event_seq (user_id, conversation_id, event_seq),
    UNIQUE KEY uk_conversation_event_producer (user_id, conversation_id, producer_key),
    CONSTRAINT fk_conversation_event_owner FOREIGN KEY (user_id, conversation_id) REFERENCES sq_conversation (user_id, conversation_id),
    CONSTRAINT ck_conversation_event_del CHECK (del_flag IN ('0', '1'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='会话一致前缀公开事件';

CREATE TABLE sq_message_artifact
(
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '存储主键ID',
    user_id CHAR(36) NOT NULL COMMENT '资源归属用户ID',
    message_id CHAR(36) NOT NULL COMMENT '消息ID',
    artifact_id CHAR(36) NOT NULL COMMENT '输入或交付产物ID',
    creator_id VARCHAR(36) NOT NULL DEFAULT '' COMMENT '创建人ID',
    creator_name VARCHAR(50) NOT NULL DEFAULT '' COMMENT '创建人姓名',
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    last_updater_id VARCHAR(36) NOT NULL DEFAULT '' COMMENT '最后更新人ID',
    last_updater_name VARCHAR(50) NOT NULL DEFAULT '' COMMENT '最后更新人姓名',
    last_update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '最后更新时间',
    del_flag CHAR(1) NOT NULL DEFAULT '0' COMMENT '删除标志[0-否,1-是]',
    PRIMARY KEY (id),
    UNIQUE KEY uk_message_artifact (user_id, message_id, artifact_id),
    CONSTRAINT fk_message_artifact_message FOREIGN KEY (user_id, message_id) REFERENCES sq_message (user_id, message_id),
    CONSTRAINT fk_message_artifact_owner FOREIGN KEY (user_id, artifact_id) REFERENCES sq_artifact (user_id, artifact_id),
    CONSTRAINT ck_message_artifact_del CHECK (del_flag IN ('0', '1'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='消息与产物归属关联';

ALTER TABLE sq_goal_task ADD COLUMN scope_id CHAR(36) NULL COMMENT '目标执行作用域ID';
ALTER TABLE sq_goal_task ADD COLUMN origin_message_id CHAR(36) NULL COMMENT 'Master创建目标的来源用户消息ID';
ALTER TABLE sq_goal_task ADD COLUMN origin_execution_id CHAR(36) NULL COMMENT 'Master创建目标的来源回复执行ID';

ALTER TABLE sq_runtime_binding ADD COLUMN scope_id CHAR(36) NULL COMMENT '统一执行作用域ID';
ALTER TABLE sq_runtime_binding ADD CONSTRAINT fk_runtime_binding_scope FOREIGN KEY (user_id, scope_id) REFERENCES sq_execution_scope (user_id, scope_id);

ALTER TABLE sq_command ADD COLUMN scope_id CHAR(36) NULL COMMENT '统一执行作用域ID';
ALTER TABLE sq_command ADD CONSTRAINT fk_command_scope FOREIGN KEY (user_id, scope_id) REFERENCES sq_execution_scope (user_id, scope_id);

ALTER TABLE sq_operation ADD COLUMN scope_id CHAR(36) NULL COMMENT '统一执行作用域ID';
ALTER TABLE sq_operation ADD CONSTRAINT fk_operation_scope FOREIGN KEY (user_id, scope_id) REFERENCES sq_execution_scope (user_id, scope_id);

ALTER TABLE sq_interaction ADD COLUMN scope_id CHAR(36) NULL COMMENT '统一执行作用域ID';
ALTER TABLE sq_interaction ADD CONSTRAINT fk_interaction_scope FOREIGN KEY (user_id, scope_id) REFERENCES sq_execution_scope (user_id, scope_id);

ALTER TABLE sq_context_snapshot ADD COLUMN scope_id CHAR(36) NULL COMMENT '统一执行作用域ID';
ALTER TABLE sq_context_snapshot ADD CONSTRAINT fk_context_snapshot_scope FOREIGN KEY (user_id, scope_id) REFERENCES sq_execution_scope (user_id, scope_id);

ALTER TABLE sq_budget_reservation ADD COLUMN scope_id CHAR(36) NULL COMMENT '真实请求执行作用域ID';
ALTER TABLE sq_budget_reservation ADD COLUMN account_id CHAR(36) NULL COMMENT '共享预算账户ID';
ALTER TABLE sq_budget_reservation ADD CONSTRAINT fk_reservation_account FOREIGN KEY (user_id, account_id) REFERENCES sq_budget_account (user_id, account_id);
ALTER TABLE sq_budget_reservation ADD CONSTRAINT fk_reservation_scope FOREIGN KEY (user_id, scope_id) REFERENCES sq_execution_scope (user_id, scope_id);
ALTER TABLE sq_goal_task ADD CONSTRAINT fk_task_scope FOREIGN KEY (user_id, scope_id) REFERENCES sq_execution_scope (user_id, scope_id);
ALTER TABLE sq_goal_task ADD CONSTRAINT fk_task_origin_message FOREIGN KEY (user_id, origin_message_id) REFERENCES sq_message (user_id, message_id);
