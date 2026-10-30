-- 普通回复不要求目标ID；执行归属由 Scope 确定，原任务外键仍保护有目标的引用。
ALTER TABLE sq_runtime_binding MODIFY COLUMN task_id CHAR(36) NULL COMMENT '可选所属目标ID；普通回复为空';
ALTER TABLE sq_operation MODIFY COLUMN task_id CHAR(36) NULL COMMENT '可选所属目标ID；普通回复为空';
ALTER TABLE sq_interaction MODIFY COLUMN task_id CHAR(36) NULL COMMENT '可选所属目标ID；普通回复为空';
ALTER TABLE sq_budget_reservation MODIFY COLUMN task_id CHAR(36) NULL COMMENT '可选目标上下文ID；预算以Scope和账户为准';

ALTER TABLE sq_budget_reservation ADD CONSTRAINT uk_scope_usage UNIQUE (user_id, scope_id, usage_key);
ALTER TABLE sq_operation ADD CONSTRAINT uk_scope_business_intent UNIQUE (user_id, scope_id, tool_key, business_intent_key);

CREATE TABLE sq_user_budget (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '存储主键ID',
    user_id CHAR(36) NOT NULL COMMENT '资源归属用户ID',
    creator_id VARCHAR(36) NOT NULL DEFAULT '' COMMENT '创建人ID',
    creator_name VARCHAR(50) NOT NULL DEFAULT '' COMMENT '创建人姓名',
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    last_updater_id VARCHAR(36) NOT NULL DEFAULT '' COMMENT '最后更新人ID',
    last_updater_name VARCHAR(50) NOT NULL DEFAULT '' COMMENT '最后更新人姓名',
    last_update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '最后更新时间',
    del_flag CHAR(1) NOT NULL DEFAULT '0' COMMENT '删除标志[0-否,1-是]',
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_budget (user_id),
    CONSTRAINT ck_user_budget_del CHECK (del_flag IN ('0', '1'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='用户全局预算预留串行锁';

CREATE TABLE sq_scope_artifact (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '存储主键ID',
    user_id CHAR(36) NOT NULL COMMENT '资源归属用户ID',
    scope_id CHAR(36) NOT NULL COMMENT '所属执行作用域ID',
    artifact_id CHAR(36) NOT NULL COMMENT '产物ID',
    creator_id VARCHAR(36) NOT NULL DEFAULT '' COMMENT '创建人ID',
    creator_name VARCHAR(50) NOT NULL DEFAULT '' COMMENT '创建人姓名',
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    last_updater_id VARCHAR(36) NOT NULL DEFAULT '' COMMENT '最后更新人ID',
    last_updater_name VARCHAR(50) NOT NULL DEFAULT '' COMMENT '最后更新人姓名',
    last_update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '最后更新时间',
    del_flag CHAR(1) NOT NULL DEFAULT '0' COMMENT '删除标志[0-否,1-是]',
    PRIMARY KEY (id),
    UNIQUE KEY uk_scope_artifact (user_id, scope_id, artifact_id),
    CONSTRAINT fk_scope_artifact_scope FOREIGN KEY (user_id, scope_id) REFERENCES sq_execution_scope (user_id, scope_id),
    CONSTRAINT fk_scope_artifact_owner FOREIGN KEY (user_id, artifact_id) REFERENCES sq_artifact (user_id, artifact_id),
    CONSTRAINT ck_scope_artifact_del CHECK (del_flag IN ('0', '1'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='执行作用域与产物关联';

INSERT INTO sq_scope_artifact (user_id, scope_id, artifact_id)
SELECT a.user_id, t.scope_id, a.artifact_id FROM sq_task_artifact a
JOIN sq_goal_task t ON t.user_id = a.user_id AND t.task_id = a.task_id
WHERE t.scope_id IS NOT NULL;

ALTER TABLE sq_operation_reconciler MODIFY COLUMN task_id CHAR(36) NULL COMMENT '可选核对目标上下文';
ALTER TABLE sq_operation_reconciler ADD COLUMN scope_id CHAR(36) NULL COMMENT '核对授权执行作用域';
UPDATE sq_operation_reconciler r SET scope_id = (SELECT t.scope_id FROM sq_goal_task t
    WHERE t.user_id = r.user_id AND t.task_id = r.task_id);
ALTER TABLE sq_operation_reconciler ADD CONSTRAINT uk_reconciler_scope UNIQUE (user_id, scope_id);
ALTER TABLE sq_operation_reconciler ADD CONSTRAINT fk_reconciler_scope FOREIGN KEY (user_id, scope_id) REFERENCES sq_execution_scope (user_id, scope_id);
