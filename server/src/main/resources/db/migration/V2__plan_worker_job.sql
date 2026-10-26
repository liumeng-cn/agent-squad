-- Agent Squad D3 多 Worker 协作：计划 DAG 与委派节点持久化。
-- sq_plan 是不可变协作计划（taskId + planVersion）；sq_worker_job 是有界委派节点，一个节点对应一个局部目标。
-- 关键事务锁序：Conversation -> GoalTask -> Plan/Job -> Operation/Interaction -> Command/Event。

CREATE TABLE sq_plan
(
    id              BIGINT      NOT NULL AUTO_INCREMENT COMMENT '存储主键ID',
    plan_id         CHAR(36)    NOT NULL COMMENT '计划业务ID',
    user_id         CHAR(36)    NOT NULL COMMENT '资源归属用户ID',
    task_id         CHAR(36)    NOT NULL COMMENT '所属目标ID',
    plan_version    BIGINT      NOT NULL COMMENT '计划版本（taskId 内递增）',
    logical_key     VARCHAR(160) NOT NULL COMMENT '计划稳定逻辑键（幂等重放）',
    status          VARCHAR(16) NOT NULL COMMENT 'ACTIVE/SUPERSEDED',
    definition_json JSON        NOT NULL COMMENT '不可变计划定义：节点/依赖/汇合标准/能力与权限版本引用',
    signal_version  BIGINT      NOT NULL DEFAULT 0 COMMENT '汇合信号版本',
    version         BIGINT      NOT NULL DEFAULT 0 COMMENT '业务版本',
    creator_id      VARCHAR(36) NOT NULL DEFAULT '' COMMENT '创建人ID',
    creator_name    VARCHAR(50) NOT NULL DEFAULT '' COMMENT '创建人姓名',
    create_time     DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    last_updater_id VARCHAR(36) NOT NULL DEFAULT '' COMMENT '最后更新人ID',
    last_updater_name VARCHAR(50) NOT NULL DEFAULT '' COMMENT '最后更新人姓名',
    last_update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '最后更新时间',
    del_flag        CHAR(1)     NOT NULL DEFAULT '0' COMMENT '删除标志[0-否,1-是]',
    PRIMARY KEY (id),
    UNIQUE KEY uk_plan (plan_id),
    UNIQUE KEY uk_plan_task_version (task_id, plan_version),
    UNIQUE KEY uk_plan_task_logical (task_id, logical_key),
    KEY            idx_plan_task (task_id),
    CONSTRAINT fk_plan_task FOREIGN KEY (user_id, task_id)
        REFERENCES sq_goal_task (user_id, task_id),
    CONSTRAINT ck_plan_status CHECK (status IN ('ACTIVE', 'SUPERSEDED')),
    CONSTRAINT ck_plan_del CHECK (del_flag IN ('0', '1'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='不可变协作计划DAG';

CREATE TABLE sq_worker_job
(
    id                    BIGINT      NOT NULL AUTO_INCREMENT COMMENT '存储主键ID',
    job_id                CHAR(36)    NOT NULL COMMENT '委派节点业务ID',
    user_id               CHAR(36)    NOT NULL COMMENT '资源归属用户ID',
    task_id               CHAR(36)    NOT NULL COMMENT '所属目标ID',
    plan_version          BIGINT      NOT NULL COMMENT '所属计划版本',
    node_key              VARCHAR(64) NOT NULL COMMENT '计划内节点键',
    worker_key            VARCHAR(80) NOT NULL COMMENT '能力键',
    worker_version        VARCHAR(40) NOT NULL COMMENT '能力版本',
    status                VARCHAR(24) NOT NULL COMMENT 'BLOCKED/READY/RUNNING/SUCCEEDED/PARTIAL/FAILED/WAITING_INPUT/WAITING_APPROVAL/RECOVERY_BLOCKED/RECONCILING/CANCELLING/CANCELLED/SUPERSEDED',
    depends_on_json       JSON NULL COMMENT '依赖节点键数组',
    input_json            JSON        NOT NULL COMMENT '固定局部输入',
    result_candidate_json JSON NULL COMMENT '结果候选（非最终结果）',
    result_json           JSON NULL COMMENT '最终结构化结果',
    binding_id            CHAR(36) NULL COMMENT '运行时绑定ID',
    reused_from_job_id    CHAR(36) NULL COMMENT '结果复用来源JobID',
    cancel_requested_at   DATETIME(3) NULL COMMENT '取消屏障时间；新计划替代在途旧Job时写入',
    version               BIGINT      NOT NULL DEFAULT 0 COMMENT '业务版本',
    creator_id            VARCHAR(36) NOT NULL DEFAULT '' COMMENT '创建人ID',
    creator_name          VARCHAR(50) NOT NULL DEFAULT '' COMMENT '创建人姓名',
    create_time           DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    last_updater_id       VARCHAR(36) NOT NULL DEFAULT '' COMMENT '最后更新人ID',
    last_updater_name     VARCHAR(50) NOT NULL DEFAULT '' COMMENT '最后更新人姓名',
    last_update_time      DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '最后更新时间',
    del_flag              CHAR(1)     NOT NULL DEFAULT '0' COMMENT '删除标志[0-否,1-是]',
    PRIMARY KEY (id),
    UNIQUE KEY uk_worker_job (job_id),
    UNIQUE KEY uk_worker_job_node (task_id, plan_version, node_key),
    KEY            idx_worker_job_status (status, id),
    CONSTRAINT fk_worker_job_task FOREIGN KEY (user_id, task_id)
        REFERENCES sq_goal_task (user_id, task_id),
    CONSTRAINT fk_worker_job_plan FOREIGN KEY (task_id, plan_version)
        REFERENCES sq_plan (task_id, plan_version),
    CONSTRAINT ck_worker_job_status CHECK (status IN ('BLOCKED', 'READY', 'RUNNING', 'SUCCEEDED', 'PARTIAL', 'FAILED', 'WAITING_INPUT', 'WAITING_APPROVAL', 'RECOVERY_BLOCKED', 'RECONCILING', 'CANCELLING', 'CANCELLED', 'SUPERSEDED')),
    CONSTRAINT ck_worker_job_del CHECK (del_flag IN ('0', '1'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='有界委派节点';
