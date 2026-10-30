-- 目标语义修订与运行状态版本分离；保留原Task/Session/预算身份。
ALTER TABLE sq_goal_task ADD COLUMN goal_revision BIGINT NOT NULL DEFAULT 0 COMMENT '已应用目标语义修订号';
ALTER TABLE sq_context_snapshot ADD COLUMN goal_revision BIGINT NOT NULL DEFAULT 0 COMMENT '准备局部材料时的目标修订号';
ALTER TABLE sq_plan ADD COLUMN goal_revision BIGINT NOT NULL DEFAULT 0 COMMENT '发布计划时的目标修订号';

CREATE TABLE sq_task_directive
(
    id                       BIGINT       NOT NULL AUTO_INCREMENT COMMENT '存储主键ID',
    directive_id             CHAR(36)     NOT NULL COMMENT '目标修订指令业务ID',
    user_id                  CHAR(36)     NOT NULL COMMENT '归属用户ID',
    conversation_id          CHAR(36)     NOT NULL COMMENT '归属会话ID',
    conversation_generation  BIGINT       NOT NULL COMMENT '受理时会话代际',
    source_message_id        CHAR(36)     NOT NULL COMMENT '受权用户消息ID',
    source_scope_id          CHAR(36)     NOT NULL COMMENT '来源回复执行作用域ID',
    task_id                  CHAR(36)     NOT NULL COMMENT '当前受权目标ID',
    logical_key              VARCHAR(160) NOT NULL COMMENT '来源Scope内稳定业务调用键',
    payload_hash             CHAR(64)     NOT NULL COMMENT '规范化受理参数摘要',
    expected_version         BIGINT       NOT NULL COMMENT '受理时校验的目标运行版本',
    expected_goal_revision   BIGINT       NOT NULL COMMENT '用户修改所基于的目标语义修订号',
    kind                     VARCHAR(20)  NOT NULL COMMENT '类型[SUPPLEMENT,REPLACE]',
    content                  LONGTEXT     NOT NULL COMMENT 'Master归纳的修改建议，不冒充用户原话',
    source_text              LONGTEXT     NOT NULL COMMENT '受权用户消息的实际原文',
    artifact_ids_json        JSON         NOT NULL COMMENT '来源消息的受权附件引用',
    status                   VARCHAR(20)  NOT NULL COMMENT '状态[ACCEPTED,APPLIED,REJECTED]',
    command_id               CHAR(36)     NULL COMMENT '独立任务协调命令ID',
    applied_version          BIGINT       NULL COMMENT '实际应用后的目标运行版本',
    applied_goal_revision    BIGINT       NULL COMMENT '实际应用后的目标语义修订号',
    reason_code              VARCHAR(64)  NULL COMMENT '未应用的结构化原因码',
    reason_message           VARCHAR(500) NULL COMMENT '可向用户解释的安全原因',
    next_attempt_at          DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '下次协调检查时间',
    creator_id               VARCHAR(36)  NOT NULL DEFAULT '' COMMENT '创建人ID',
    creator_name             VARCHAR(50)  NOT NULL DEFAULT '' COMMENT '创建人姓名',
    create_time              DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    last_updater_id          VARCHAR(36)  NOT NULL DEFAULT '' COMMENT '最后更新人ID',
    last_updater_name        VARCHAR(50)  NOT NULL DEFAULT '' COMMENT '最后更新人姓名',
    last_update_time         DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '最后更新时间',
    del_flag                 CHAR(1)      NOT NULL DEFAULT '0' COMMENT '删除标志[0-否,1-是]',
    PRIMARY KEY (id),
    UNIQUE KEY uk_task_directive (user_id, directive_id),
    UNIQUE KEY uk_directive_source_key (user_id, source_scope_id, logical_key),
    KEY idx_directive_due (status, next_attempt_at, id),
    KEY idx_directive_task (user_id, task_id, id),
    CONSTRAINT fk_directive_conversation FOREIGN KEY (user_id, conversation_id) REFERENCES sq_conversation (user_id, conversation_id),
    CONSTRAINT fk_directive_source FOREIGN KEY (user_id, source_message_id) REFERENCES sq_message (user_id, message_id),
    CONSTRAINT fk_directive_scope FOREIGN KEY (user_id, source_scope_id) REFERENCES sq_execution_scope (user_id, scope_id),
    CONSTRAINT fk_directive_task FOREIGN KEY (user_id, task_id) REFERENCES sq_goal_task (user_id, task_id),
    CONSTRAINT ck_directive_kind CHECK (kind IN ('SUPPLEMENT', 'REPLACE')),
    CONSTRAINT ck_directive_status CHECK (status IN ('ACCEPTED', 'APPLIED', 'REJECTED')),
    CONSTRAINT ck_directive_versions CHECK (expected_version >= 0 AND expected_goal_revision >= 0),
    CONSTRAINT ck_directive_del CHECK (del_flag IN ('0', '1'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='用户目标修改的持久受理与应用事实';

ALTER TABLE sq_runtime_binding ADD COLUMN last_ended_turn_id VARCHAR(64) NULL COMMENT '已完成业务投影的最近原生TurnID';
