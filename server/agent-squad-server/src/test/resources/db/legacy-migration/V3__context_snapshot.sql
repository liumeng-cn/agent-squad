-- D3 局部上下文快照；只冻结已归属用户目标中的材料，不开放任意来源或自动长期记忆。
CREATE TABLE sq_context_snapshot
(
    id                      BIGINT       NOT NULL AUTO_INCREMENT COMMENT '存储主键ID',
    snapshot_id             CHAR(36)     NOT NULL COMMENT '局部上下文快照业务ID',
    user_id                 CHAR(36)     NOT NULL COMMENT '资源归属用户ID',
    task_id                 CHAR(36)     NOT NULL COMMENT '所属目标ID',
    target_node_key         VARCHAR(80)  NOT NULL COMMENT '目标计划节点键',
    logical_key             VARCHAR(160) NOT NULL COMMENT '快照幂等逻辑键',
    conversation_generation BIGINT       NOT NULL COMMENT '冻结时的对话代际',
    memory_revision         BIGINT       NOT NULL DEFAULT 0 COMMENT '记忆授权代际；D3 无长期记忆时为0',
    items_json              JSON         NOT NULL COMMENT '经平台验证的局部材料及来源',
    token_estimate          BIGINT       NOT NULL COMMENT '局部材料令牌估算',
    content_hash            CHAR(64)     NOT NULL COMMENT '局部材料规范化SHA256摘要',
    creator_id              VARCHAR(36)  NOT NULL DEFAULT '' COMMENT '创建人ID',
    creator_name            VARCHAR(50)  NOT NULL DEFAULT '' COMMENT '创建人姓名',
    create_time             DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    last_updater_id         VARCHAR(36)  NOT NULL DEFAULT '' COMMENT '最后更新人ID',
    last_updater_name       VARCHAR(50)  NOT NULL DEFAULT '' COMMENT '最后更新人姓名',
    last_update_time        DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '最后更新时间',
    del_flag                CHAR(1)      NOT NULL DEFAULT '0' COMMENT '删除标志[0-否,1-是]',
    PRIMARY KEY (id),
    UNIQUE KEY uk_context_snapshot (snapshot_id),
    UNIQUE KEY uk_context_logical (task_id, target_node_key, logical_key),
    KEY idx_context_owner_task (user_id, task_id),
    CONSTRAINT fk_context_task FOREIGN KEY (user_id, task_id)
        REFERENCES sq_goal_task (user_id, task_id),
    CONSTRAINT ck_context_del CHECK (del_flag IN ('0', '1'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='Worker授权局部上下文快照';

-- V2 旧节点键为64字符；与 OpenAPI 中 nodeKey 上限80对齐，保留已执行迁移的校验和。
ALTER TABLE sq_worker_job MODIFY COLUMN node_key VARCHAR(80) NOT NULL COMMENT '计划内节点键';
