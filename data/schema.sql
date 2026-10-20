-- Agent Squad v0.2 业务数据设计；MySQL 8.4；仅设计，未执行。
-- 框架原生表由固定SDK版本参考DDL另行迁移，不在此冒充完成。
-- 关键事务锁序：Conversation -> GoalTask -> Plan/Job -> Operation/Interaction -> Command/Event。

CREATE TABLE `sq_account` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '存储主键ID',
    `user_id` CHAR(36) NOT NULL COMMENT '用户业务ID',
    `username` VARCHAR(64) NOT NULL COMMENT '登录名',
    `password_hash` VARCHAR(255) NOT NULL COMMENT '密码散列',
    `roles_json` JSON NOT NULL COMMENT '服务端维护的角色集合[USER/ADMIN/OPERATOR]',
    `status` VARCHAR(16) NOT NULL COMMENT '状态[ACTIVE/DISABLED]',
    `auth_version` BIGINT NOT NULL DEFAULT 0 COMMENT '授权失效版本',
    `memory_revision` BIGINT NOT NULL DEFAULT 0 COMMENT '显式记忆全局版本',
    `creator_id` VARCHAR(36) NOT NULL DEFAULT '' COMMENT '创建人ID',
    `creator_name` VARCHAR(50) NOT NULL DEFAULT '' COMMENT '创建人姓名',
    `create_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    `last_updater_id` VARCHAR(36) NOT NULL DEFAULT '' COMMENT '最后更新人ID',
    `last_updater_name` VARCHAR(50) NOT NULL DEFAULT '' COMMENT '最后更新人姓名',
    `last_update_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '最后更新时间',
    `del_flag` CHAR(1) NOT NULL DEFAULT '0' COMMENT '删除标志[0-否,1-是]',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user` (`user_id`),
    UNIQUE KEY `uk_username` (`username`),
    CONSTRAINT `ck_account_del` CHECK (`del_flag` IN ('0', '1'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='平台账号';

CREATE TABLE `sq_auth_session` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '存储主键ID',
    `session_id` CHAR(36) NOT NULL COMMENT '登录会话业务ID',
    `user_id` CHAR(36) NOT NULL COMMENT '资源归属用户ID',
    `token_hash` VARCHAR(64) NOT NULL COMMENT '随机令牌摘要',
    `csrf_hash` VARCHAR(64) NOT NULL COMMENT 'CSRF令牌摘要',
    `auth_version` BIGINT NOT NULL DEFAULT 0 COMMENT '登录时授权版本',
    `expires_at` DATETIME(3) NOT NULL COMMENT '过期时间',
    `creator_id` VARCHAR(36) NOT NULL DEFAULT '' COMMENT '创建人ID',
    `creator_name` VARCHAR(50) NOT NULL DEFAULT '' COMMENT '创建人姓名',
    `create_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    `last_updater_id` VARCHAR(36) NOT NULL DEFAULT '' COMMENT '最后更新人ID',
    `last_updater_name` VARCHAR(50) NOT NULL DEFAULT '' COMMENT '最后更新人姓名',
    `last_update_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '最后更新时间',
    `del_flag` CHAR(1) NOT NULL DEFAULT '0' COMMENT '删除标志[0-否,1-是]',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_session` (`session_id`),
    UNIQUE KEY `uk_token` (`token_hash`),
    KEY `idx_expire` (`expires_at`),
    CONSTRAINT `ck_auth_session_del` CHECK (`del_flag` IN ('0', '1'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='登录会话';

CREATE TABLE `sq_conversation` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '存储主键ID',
    `conversation_id` CHAR(36) NOT NULL COMMENT '对话业务ID',
    `user_id` CHAR(36) NOT NULL COMMENT '资源归属用户ID',
    `title` VARCHAR(120) NOT NULL COMMENT '会话标题',
    `generation` BIGINT NOT NULL DEFAULT 0 COMMENT '删除与重建隔离代数',
    `status` VARCHAR(16) NOT NULL COMMENT '状态[ACTIVE/DELETING/DELETED]',
    `active_task_id` CHAR(36) NULL COMMENT '未终结目标ID；受控循环引用',
    `version` BIGINT NOT NULL DEFAULT 0 COMMENT '业务乐观版本',
    `creator_id` VARCHAR(36) NOT NULL DEFAULT '' COMMENT '创建人ID',
    `creator_name` VARCHAR(50) NOT NULL DEFAULT '' COMMENT '创建人姓名',
    `create_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    `last_updater_id` VARCHAR(36) NOT NULL DEFAULT '' COMMENT '最后更新人ID',
    `last_updater_name` VARCHAR(50) NOT NULL DEFAULT '' COMMENT '最后更新人姓名',
    `last_update_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '最后更新时间',
    `del_flag` CHAR(1) NOT NULL DEFAULT '0' COMMENT '删除标志[0-否,1-是]',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_owner_conversation` (`user_id`, `conversation_id`),
    CONSTRAINT `ck_conversation_del` CHECK (`del_flag` IN ('0', '1'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='用户对话';

CREATE TABLE `sq_goal_task` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '存储主键ID',
    `task_id` CHAR(36) NOT NULL COMMENT '用户目标ID；不是框架RunID',
    `user_id` CHAR(36) NOT NULL COMMENT '资源归属用户ID',
    `conversation_id` CHAR(36) NOT NULL COMMENT '所属对话ID',
    `status` VARCHAR(24) NOT NULL COMMENT '产品状态；见M02',
    `version` BIGINT NOT NULL DEFAULT 0 COMMENT '业务乐观版本',
    `plan_version` BIGINT NOT NULL DEFAULT 0 COMMENT '当前计划版本',
    `signal_version` BIGINT NOT NULL DEFAULT 0 COMMENT '待协调结果水位',
    `handled_signal_version` BIGINT NOT NULL DEFAULT 0 COMMENT '已受理协调结果水位',
    `cancel_generation` BIGINT NOT NULL DEFAULT 0 COMMENT '取消屏障代数',
    `cancel_requested_at` DATETIME(3) NULL COMMENT '取消意图时间',
    `answer_revision` BIGINT NOT NULL DEFAULT 0 COMMENT '公开正文版本',
    `event_seq` BIGINT NOT NULL DEFAULT 0 COMMENT '公开事件最新水位',
    `input_json` JSON NOT NULL COMMENT 'TaskCreate规范化输入',
    `profile_json` JSON NOT NULL COMMENT 'BudgetProfile冻结值',
    `policy_json` JSON NOT NULL COMMENT '冻结权限上限和版本',
    `completion_proposal_json` JSON NULL COMMENT 'CompletionProposal候选；非最终成功',
    `deadline_at` DATETIME(3) NOT NULL COMMENT '总期限',
    `blocked_reason` VARCHAR(1000) NULL COMMENT '恢复阻塞原因',
    `creator_id` VARCHAR(36) NOT NULL DEFAULT '' COMMENT '创建人ID',
    `creator_name` VARCHAR(50) NOT NULL DEFAULT '' COMMENT '创建人姓名',
    `create_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    `last_updater_id` VARCHAR(36) NOT NULL DEFAULT '' COMMENT '最后更新人ID',
    `last_updater_name` VARCHAR(50) NOT NULL DEFAULT '' COMMENT '最后更新人姓名',
    `last_update_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '最后更新时间',
    `del_flag` CHAR(1) NOT NULL DEFAULT '0' COMMENT '删除标志[0-否,1-是]',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_owner_task` (`user_id`, `task_id`),
    KEY `idx_status_due` (`status`, `deadline_at`),
    CONSTRAINT `ck_goal_task_del` CHECK (`del_flag` IN ('0', '1'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='用户目标及协作生命周期';

CREATE TABLE `sq_plan` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '存储主键ID',
    `plan_id` CHAR(36) NOT NULL COMMENT '计划业务ID',
    `user_id` CHAR(36) NOT NULL COMMENT '资源归属用户ID',
    `task_id` CHAR(36) NOT NULL COMMENT '所属目标ID',
    `plan_version` BIGINT NOT NULL DEFAULT 0 COMMENT '计划版本',
    `logical_key` VARCHAR(160) NOT NULL COMMENT 'Master工具幂等键',
    `request_hash` VARCHAR(64) NOT NULL COMMENT '规范化计划摘要',
    `definition_json` JSON NOT NULL COMMENT 'PlanPublish完整结构',
    `status` VARCHAR(20) NOT NULL COMMENT '状态[ACTIVE/SUPERSEDED]',
    `creator_id` VARCHAR(36) NOT NULL DEFAULT '' COMMENT '创建人ID',
    `creator_name` VARCHAR(50) NOT NULL DEFAULT '' COMMENT '创建人姓名',
    `create_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    `last_updater_id` VARCHAR(36) NOT NULL DEFAULT '' COMMENT '最后更新人ID',
    `last_updater_name` VARCHAR(50) NOT NULL DEFAULT '' COMMENT '最后更新人姓名',
    `last_update_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '最后更新时间',
    `del_flag` CHAR(1) NOT NULL DEFAULT '0' COMMENT '删除标志[0-否,1-是]',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_plan_version` (`user_id`, `task_id`, `plan_version`),
    UNIQUE KEY `uk_plan_key` (`user_id`, `task_id`, `logical_key`),
    CONSTRAINT `ck_plan_del` CHECK (`del_flag` IN ('0', '1'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='不可变协作计划';

CREATE TABLE `sq_worker_job` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '存储主键ID',
    `job_id` CHAR(36) NOT NULL COMMENT '节点业务ID',
    `user_id` CHAR(36) NOT NULL COMMENT '资源归属用户ID',
    `task_id` CHAR(36) NOT NULL COMMENT '所属目标ID',
    `plan_version` BIGINT NOT NULL DEFAULT 0 COMMENT '所属计划版本',
    `node_key` VARCHAR(80) NOT NULL COMMENT '计划节点键',
    `worker_key` VARCHAR(80) NOT NULL COMMENT '能力键',
    `worker_version` VARCHAR(40) NOT NULL COMMENT '能力版本',
    `context_snapshot_id` CHAR(36) NOT NULL COMMENT '冻结局部输入ID',
    `depends_on_json` JSON NOT NULL COMMENT '依赖nodeKey数组；来源PlanPublish',
    `status` VARCHAR(24) NOT NULL COMMENT '业务执行状态；见M04',
    `version` BIGINT NOT NULL DEFAULT 0 COMMENT '业务乐观版本',
    `recovery_count` BIGINT NOT NULL DEFAULT 0 COMMENT '恢复计数',
    `reused_from_job_id` CHAR(36) NULL COMMENT '成功结果复用来源',
    `result_candidate_json` JSON NULL COMMENT 'JobResult候选；等原生Turn完成',
    `result_json` JSON NULL COMMENT '经Finalizer确认的JobResult',
    `creator_id` VARCHAR(36) NOT NULL DEFAULT '' COMMENT '创建人ID',
    `creator_name` VARCHAR(50) NOT NULL DEFAULT '' COMMENT '创建人姓名',
    `create_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    `last_updater_id` VARCHAR(36) NOT NULL DEFAULT '' COMMENT '最后更新人ID',
    `last_updater_name` VARCHAR(50) NOT NULL DEFAULT '' COMMENT '最后更新人姓名',
    `last_update_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '最后更新时间',
    `del_flag` CHAR(1) NOT NULL DEFAULT '0' COMMENT '删除标志[0-否,1-是]',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_owner_job` (`user_id`, `job_id`),
    UNIQUE KEY `uk_plan_node` (`user_id`, `task_id`, `plan_version`, `node_key`),
    KEY `idx_job_ready` (`status`, `create_time`),
    CONSTRAINT `ck_worker_job_del` CHECK (`del_flag` IN ('0', '1'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='持久委派节点';

CREATE TABLE `sq_runtime_binding` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '存储主键ID',
    `binding_id` CHAR(36) NOT NULL COMMENT '绑定业务ID',
    `user_id` CHAR(36) NOT NULL COMMENT '资源归属用户ID',
    `task_id` CHAR(36) NOT NULL COMMENT '所属目标ID',
    `job_id` CHAR(36) NULL COMMENT 'Worker节点ID；Master为空',
    `role` VARCHAR(16) NOT NULL COMMENT '角色[MASTER/WORKER]',
    `namespace` VARCHAR(160) NOT NULL COMMENT '框架存储命名空间',
    `agent_id` VARCHAR(160) NOT NULL COMMENT '固定Agent能力版本标识',
    `framework_session_id` VARCHAR(160) NOT NULL COMMENT '稳定原生会话ID',
    `framework_version` VARCHAR(40) NOT NULL COMMENT '固定SDK版本',
    `config_digest` VARCHAR(64) NOT NULL COMMENT '模型工具技能配置摘要',
    `status` VARCHAR(24) NOT NULL COMMENT '生命周期[IDLE/ACTIVE/BLOCKED/STOPPED/DIRTY]',
    `guard_owner` VARCHAR(100) NULL COMMENT '平台实例ID',
    `guard_fence` BIGINT NOT NULL DEFAULT 0 COMMENT '平台资源授权栅栏',
    `guard_until` DATETIME(3) NULL COMMENT '平台租约截止；不是原生writer lease',
    `latest_turn_id` VARCHAR(200) NULL COMMENT '最新Turn引用；只读投影',
    `latest_run_id` VARCHAR(200) NULL COMMENT '最新Run引用；只读投影',
    `native_seq` BIGINT NOT NULL DEFAULT 0 COMMENT '已观察原生日志水位',
    `creator_id` VARCHAR(36) NOT NULL DEFAULT '' COMMENT '创建人ID',
    `creator_name` VARCHAR(50) NOT NULL DEFAULT '' COMMENT '创建人姓名',
    `create_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    `last_updater_id` VARCHAR(36) NOT NULL DEFAULT '' COMMENT '最后更新人ID',
    `last_updater_name` VARCHAR(50) NOT NULL DEFAULT '' COMMENT '最后更新人姓名',
    `last_update_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '最后更新时间',
    `del_flag` CHAR(1) NOT NULL DEFAULT '0' COMMENT '删除标志[0-否,1-是]',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_owner_binding` (`user_id`, `binding_id`),
    UNIQUE KEY `uk_framework_identity` (`user_id`, `namespace`, `agent_id`, `framework_session_id`),
    KEY `idx_binding_due` (`status`, `guard_until`),
    CONSTRAINT `ck_runtime_binding_del` CHECK (`del_flag` IN ('0', '1'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='框架会话绑定及发现目录';

CREATE TABLE `sq_command` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '存储主键ID',
    `command_id` CHAR(36) NOT NULL COMMENT '命令业务ID',
    `user_id` CHAR(36) NOT NULL COMMENT '资源归属用户ID',
    `scope_key` VARCHAR(160) NOT NULL COMMENT '幂等作用域[动作和资源]',
    `idempotency_key` VARCHAR(160) NOT NULL COMMENT '调用方稳定键',
    `payload_hash` VARCHAR(64) NOT NULL COMMENT '请求摘要',
    `task_id` CHAR(36) NULL COMMENT '目标ID；非任务命令可空',
    `binding_id` CHAR(36) NULL COMMENT '目标框架绑定ID；非框架命令可空',
    `action` VARCHAR(32) NOT NULL COMMENT 'SUBMIT/MASTER_SIGNAL/RESPOND/RESUME/STOP/CLEANUP或资源写入动作',
    `payload_json` JSON NOT NULL COMMENT '规范化输入或CommandPayload；不得含原始密钥',
    `status` VARCHAR(20) NOT NULL COMMENT 'PENDING/DELIVERING/DELIVERED/APPLIED/BLOCKED/CANCELLED',
    `receipt_json` JSON NULL COMMENT '公共回执及原生收据引用',
    `due_at` DATETIME(3) NOT NULL COMMENT '下次投递时间',
    `lease_owner` VARCHAR(100) NULL COMMENT '桥接实例',
    `fence` BIGINT NOT NULL DEFAULT 0 COMMENT '投递栅栏',
    `lease_until` DATETIME(3) NULL COMMENT '投递租约',
    `attempt_count` BIGINT NOT NULL DEFAULT 0 COMMENT '投递次数',
    `creator_id` VARCHAR(36) NOT NULL DEFAULT '' COMMENT '创建人ID',
    `creator_name` VARCHAR(50) NOT NULL DEFAULT '' COMMENT '创建人姓名',
    `create_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    `last_updater_id` VARCHAR(36) NOT NULL DEFAULT '' COMMENT '最后更新人ID',
    `last_updater_name` VARCHAR(50) NOT NULL DEFAULT '' COMMENT '最后更新人姓名',
    `last_update_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '最后更新时间',
    `del_flag` CHAR(1) NOT NULL DEFAULT '0' COMMENT '删除标志[0-否,1-是]',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_command` (`command_id`),
    UNIQUE KEY `uk_owner_scope_key` (`user_id`, `scope_key`, `idempotency_key`),
    KEY `idx_command_due` (`status`, `due_at`),
    CONSTRAINT `ck_command_del` CHECK (`del_flag` IN ('0', '1'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='持久命令桥接与幂等回执';

CREATE TABLE `sq_context_snapshot` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '存储主键ID',
    `snapshot_id` CHAR(36) NOT NULL COMMENT '快照ID',
    `user_id` CHAR(36) NOT NULL COMMENT '资源归属用户ID',
    `task_id` CHAR(36) NOT NULL COMMENT '所属目标ID',
    `target_node_key` VARCHAR(80) NULL COMMENT '发布计划前绑定节点键；Master可空',
    `conversation_generation` BIGINT NOT NULL DEFAULT 0 COMMENT '会话代数',
    `memory_revision` BIGINT NOT NULL DEFAULT 0 COMMENT '记忆版本',
    `content_hash` VARCHAR(64) NOT NULL COMMENT '内容摘要',
    `snapshot_json` JSON NOT NULL COMMENT 'ContextSnapshot完整结构',
    `creator_id` VARCHAR(36) NOT NULL DEFAULT '' COMMENT '创建人ID',
    `creator_name` VARCHAR(50) NOT NULL DEFAULT '' COMMENT '创建人姓名',
    `create_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    `last_updater_id` VARCHAR(36) NOT NULL DEFAULT '' COMMENT '最后更新人ID',
    `last_updater_name` VARCHAR(50) NOT NULL DEFAULT '' COMMENT '最后更新人姓名',
    `last_update_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '最后更新时间',
    `del_flag` CHAR(1) NOT NULL DEFAULT '0' COMMENT '删除标志[0-否,1-是]',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_owner_snapshot` (`user_id`, `snapshot_id`),
    CONSTRAINT `ck_context_snapshot_del` CHECK (`del_flag` IN ('0', '1'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='不可变授权输入快照';

CREATE TABLE `sq_worker_definition` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '存储主键ID',
    `definition_id` CHAR(36) NOT NULL COMMENT '能力定义ID',
    `worker_key` VARCHAR(80) NOT NULL COMMENT '能力键',
    `worker_version` VARCHAR(40) NOT NULL COMMENT '不可变版本',
    `status` VARCHAR(16) NOT NULL COMMENT 'ENABLED/DISABLED/REVOKED',
    `version` BIGINT NOT NULL DEFAULT 0 COMMENT '状态乐观版本',
    `definition_json` JSON NOT NULL COMMENT 'WorkerDefinitionCreate完整结构',
    `creator_id` VARCHAR(36) NOT NULL DEFAULT '' COMMENT '创建人ID',
    `creator_name` VARCHAR(50) NOT NULL DEFAULT '' COMMENT '创建人姓名',
    `create_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    `last_updater_id` VARCHAR(36) NOT NULL DEFAULT '' COMMENT '最后更新人ID',
    `last_updater_name` VARCHAR(50) NOT NULL DEFAULT '' COMMENT '最后更新人姓名',
    `last_update_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '最后更新时间',
    `del_flag` CHAR(1) NOT NULL DEFAULT '0' COMMENT '删除标志[0-否,1-是]',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_definition` (`definition_id`),
    UNIQUE KEY `uk_worker_version` (`worker_key`, `worker_version`),
    CONSTRAINT `ck_worker_definition_del` CHECK (`del_flag` IN ('0', '1'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='不可变Worker能力定义';

CREATE TABLE `sq_skill_version` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '存储主键ID',
    `skill_version_id` CHAR(36) NOT NULL COMMENT '技能版本ID',
    `user_id` CHAR(36) NOT NULL COMMENT '资源归属用户ID',
    `name` VARCHAR(100) NOT NULL COMMENT '技能名称',
    `version` VARCHAR(80) NOT NULL COMMENT '展示版本',
    `content_hash` VARCHAR(64) NOT NULL COMMENT '规范化文本摘要',
    `description` VARCHAR(4000) NOT NULL COMMENT '发现描述',
    `status` VARCHAR(16) NOT NULL COMMENT 'ENABLED/DISABLED/REVOKED',
    `package_json` JSON NOT NULL COMMENT 'SkillImport规范化UTF8文本；总量受限',
    `creator_id` VARCHAR(36) NOT NULL DEFAULT '' COMMENT '创建人ID',
    `creator_name` VARCHAR(50) NOT NULL DEFAULT '' COMMENT '创建人姓名',
    `create_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    `last_updater_id` VARCHAR(36) NOT NULL DEFAULT '' COMMENT '最后更新人ID',
    `last_updater_name` VARCHAR(50) NOT NULL DEFAULT '' COMMENT '最后更新人姓名',
    `last_update_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '最后更新时间',
    `del_flag` CHAR(1) NOT NULL DEFAULT '0' COMMENT '删除标志[0-否,1-是]',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_owner_skill` (`user_id`, `skill_version_id`),
    UNIQUE KEY `uk_skill_hash` (`user_id`, `name`, `content_hash`),
    CONSTRAINT `ck_skill_version_del` CHECK (`del_flag` IN ('0', '1'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='不可变技能文本包';

CREATE TABLE `sq_skill_grant` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '存储主键ID',
    `user_id` CHAR(36) NOT NULL COMMENT '资源归属用户ID',
    `skill_version_id` CHAR(36) NOT NULL COMMENT '技能固定版本ID',
    `enabled` TINYINT NOT NULL DEFAULT 1 COMMENT '是否启用[0/1]',
    `version` BIGINT NOT NULL DEFAULT 0 COMMENT '授权乐观版本',
    `creator_id` VARCHAR(36) NOT NULL DEFAULT '' COMMENT '创建人ID',
    `creator_name` VARCHAR(50) NOT NULL DEFAULT '' COMMENT '创建人姓名',
    `create_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    `last_updater_id` VARCHAR(36) NOT NULL DEFAULT '' COMMENT '最后更新人ID',
    `last_updater_name` VARCHAR(50) NOT NULL DEFAULT '' COMMENT '最后更新人姓名',
    `last_update_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '最后更新时间',
    `del_flag` CHAR(1) NOT NULL DEFAULT '0' COMMENT '删除标志[0-否,1-是]',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_grant` (`user_id`, `skill_version_id`),
    CONSTRAINT `ck_skill_grant_del` CHECK (`del_flag` IN ('0', '1'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='个人技能可见授权';

CREATE TABLE `sq_connection` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '存储主键ID',
    `connection_id` CHAR(36) NOT NULL COMMENT '连接业务ID',
    `user_id` CHAR(36) NOT NULL COMMENT '资源归属用户ID',
    `name` VARCHAR(100) NOT NULL COMMENT '显示名称',
    `provider` VARCHAR(80) NOT NULL COMMENT '提供方编码',
    `environment` VARCHAR(80) NOT NULL COMMENT '环境标识',
    `resource_policy_json` JSON NOT NULL COMMENT '受控资源范围',
    `secret_ref` VARCHAR(240) NOT NULL COMMENT '密钥管理引用；不含明文',
    `revision` BIGINT NOT NULL DEFAULT 0 COMMENT '配置授权版本',
    `enabled` TINYINT NOT NULL DEFAULT 1 COMMENT '是否启用[0/1]',
    `creator_id` VARCHAR(36) NOT NULL DEFAULT '' COMMENT '创建人ID',
    `creator_name` VARCHAR(50) NOT NULL DEFAULT '' COMMENT '创建人姓名',
    `create_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    `last_updater_id` VARCHAR(36) NOT NULL DEFAULT '' COMMENT '最后更新人ID',
    `last_updater_name` VARCHAR(50) NOT NULL DEFAULT '' COMMENT '最后更新人姓名',
    `last_update_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '最后更新时间',
    `del_flag` CHAR(1) NOT NULL DEFAULT '0' COMMENT '删除标志[0-否,1-是]',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_owner_connection` (`user_id`, `connection_id`),
    CONSTRAINT `ck_connection_del` CHECK (`del_flag` IN ('0', '1'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='用户外部连接凭据引用';

CREATE TABLE `sq_operation` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '存储主键ID',
    `operation_id` CHAR(36) NOT NULL COMMENT '操作ID',
    `user_id` CHAR(36) NOT NULL COMMENT '资源归属用户ID',
    `task_id` CHAR(36) NOT NULL COMMENT '所属目标ID',
    `job_id` CHAR(36) NULL COMMENT '节点ID；Master可空',
    `binding_id` CHAR(36) NOT NULL COMMENT '原生调用所属绑定',
    `framework_turn_id` VARCHAR(200) NOT NULL COMMENT '原生TurnID',
    `tool_call_id` VARCHAR(200) NOT NULL COMMENT '原生稳定工具调用ID',
    `tool_key` VARCHAR(100) NOT NULL COMMENT '工具键',
    `tool_version` VARCHAR(40) NOT NULL COMMENT '工具版本',
    `business_intent_key` VARCHAR(160) NULL COMMENT '业务写入幂等键；READ可空',
    `effect` VARCHAR(16) NOT NULL COMMENT 'READ/WRITE/SANDBOX',
    `connection_id` CHAR(36) NULL COMMENT '用户连接ID',
    `connection_revision` BIGINT NOT NULL DEFAULT 0 COMMENT '派发时连接版本',
    `auth_version` BIGINT NOT NULL DEFAULT 0 COMMENT '派发时授权版本',
    `request_hash` VARCHAR(64) NOT NULL COMMENT '规范化参数摘要',
    `arguments_json` JSON NOT NULL COMMENT '脱敏参数；敏感值用引用',
    `status` VARCHAR(24) NOT NULL COMMENT 'PREPARED/WAITING_APPROVAL/IN_FLIGHT/UNKNOWN/RECONCILING/SUCCEEDED/FAILED/CANCELLED',
    `version` BIGINT NOT NULL DEFAULT 0 COMMENT '业务乐观版本',
    `executor_owner` VARCHAR(100) NULL COMMENT '独立执行器实例',
    `executor_fence` BIGINT NOT NULL DEFAULT 0 COMMENT '独立执行器栅栏',
    `executor_until` DATETIME(3) NULL COMMENT '执行器租约',
    `external_key` VARCHAR(200) NULL COMMENT '外部幂等或查询关联号',
    `result_json` JSON NULL COMMENT '可信连接器结果或核对事实',
    `creator_id` VARCHAR(36) NOT NULL DEFAULT '' COMMENT '创建人ID',
    `creator_name` VARCHAR(50) NOT NULL DEFAULT '' COMMENT '创建人姓名',
    `create_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    `last_updater_id` VARCHAR(36) NOT NULL DEFAULT '' COMMENT '最后更新人ID',
    `last_updater_name` VARCHAR(50) NOT NULL DEFAULT '' COMMENT '最后更新人姓名',
    `last_update_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '最后更新时间',
    `del_flag` CHAR(1) NOT NULL DEFAULT '0' COMMENT '删除标志[0-否,1-是]',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_owner_operation` (`user_id`, `operation_id`),
    UNIQUE KEY `uk_native_call` (`user_id`, `binding_id`, `framework_turn_id`, `tool_call_id`),
    UNIQUE KEY `uk_business_intent` (`user_id`, `task_id`, `tool_key`, `business_intent_key`),
    KEY `idx_operation_unknown` (`status`, `last_update_time`),
    CONSTRAINT `ck_operation_del` CHECK (`del_flag` IN ('0', '1'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='外部工具副作用账本';

CREATE TABLE `sq_interaction` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '存储主键ID',
    `interaction_id` CHAR(36) NOT NULL COMMENT '交互ID',
    `user_id` CHAR(36) NOT NULL COMMENT '资源归属用户ID',
    `task_id` CHAR(36) NOT NULL COMMENT '目标ID',
    `job_id` CHAR(36) NULL COMMENT '节点ID',
    `binding_id` CHAR(36) NULL COMMENT '所属绑定',
    `operation_id` CHAR(36) NULL COMMENT '关联操作',
    `origin` VARCHAR(16) NOT NULL COMMENT 'BUSINESS/FRAMEWORK',
    `framework_request_id` VARCHAR(200) NULL COMMENT '真实原生请求ID；业务交互为空',
    `kind` VARCHAR(16) NOT NULL COMMENT 'INPUT/APPROVAL',
    `question` VARCHAR(4000) NOT NULL COMMENT '用户问题',
    `answer_schema_json` JSON NOT NULL COMMENT '回复JSON Schema',
    `plan_version` BIGINT NOT NULL DEFAULT 0 COMMENT '绑定计划版本',
    `request_hash` VARCHAR(64) NOT NULL COMMENT '绑定请求摘要',
    `auth_version` BIGINT NOT NULL DEFAULT 0 COMMENT '确认授权版本',
    `connection_revision` BIGINT NOT NULL DEFAULT 0 COMMENT '确认连接版本',
    `expires_at` DATETIME(3) NOT NULL COMMENT '答复有效期',
    `status` VARCHAR(16) NOT NULL COMMENT 'OPEN/RESPONDED/EXPIRED/INVALIDATED/CANCELLED',
    `version` BIGINT NOT NULL DEFAULT 0 COMMENT '乐观版本',
    `response_json` JSON NULL COMMENT '受Schema校验的用户回复',
    `creator_id` VARCHAR(36) NOT NULL DEFAULT '' COMMENT '创建人ID',
    `creator_name` VARCHAR(50) NOT NULL DEFAULT '' COMMENT '创建人姓名',
    `create_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    `last_updater_id` VARCHAR(36) NOT NULL DEFAULT '' COMMENT '最后更新人ID',
    `last_updater_name` VARCHAR(50) NOT NULL DEFAULT '' COMMENT '最后更新人姓名',
    `last_update_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '最后更新时间',
    `del_flag` CHAR(1) NOT NULL DEFAULT '0' COMMENT '删除标志[0-否,1-是]',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_owner_interaction` (`user_id`, `interaction_id`),
    UNIQUE KEY `uk_framework_request` (`user_id`, `binding_id`, `framework_request_id`),
    CONSTRAINT `ck_interaction_del` CHECK (`del_flag` IN ('0', '1'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='持久输入与审批请求';

CREATE TABLE `sq_memory` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '存储主键ID',
    `memory_id` CHAR(36) NOT NULL COMMENT '记忆ID',
    `user_id` CHAR(36) NOT NULL COMMENT '资源归属用户ID',
    `memory_key` VARCHAR(100) NOT NULL COMMENT '用户维度稳定记忆键',
    `content` VARCHAR(8000) NOT NULL COMMENT '记忆内容',
    `source_task_id` CHAR(36) NULL COMMENT '来源目标；手工写入可空',
    `status` VARCHAR(16) NOT NULL COMMENT 'ACTIVE/DELETED/EXPIRED',
    `version` BIGINT NOT NULL DEFAULT 0 COMMENT '纠正删除版本',
    `expires_at` DATETIME(3) NULL COMMENT '可选过期时间',
    `creator_id` VARCHAR(36) NOT NULL DEFAULT '' COMMENT '创建人ID',
    `creator_name` VARCHAR(50) NOT NULL DEFAULT '' COMMENT '创建人姓名',
    `create_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    `last_updater_id` VARCHAR(36) NOT NULL DEFAULT '' COMMENT '最后更新人ID',
    `last_updater_name` VARCHAR(50) NOT NULL DEFAULT '' COMMENT '最后更新人姓名',
    `last_update_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '最后更新时间',
    `del_flag` CHAR(1) NOT NULL DEFAULT '0' COMMENT '删除标志[0-否,1-是]',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_owner_memory` (`user_id`, `memory_id`),
    UNIQUE KEY `uk_memory_key` (`user_id`, `memory_key`),
    CONSTRAINT `ck_memory_del` CHECK (`del_flag` IN ('0', '1'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='用户显式长期记忆';

CREATE TABLE `sq_artifact` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '存储主键ID',
    `artifact_id` CHAR(36) NOT NULL COMMENT '产物ID',
    `user_id` CHAR(36) NOT NULL COMMENT '资源归属用户ID',
    `task_id` CHAR(36) NULL COMMENT '目标；用户预上传可空',
    `job_id` CHAR(36) NULL COMMENT '节点；用户预上传可空',
    `file_name` VARCHAR(240) NOT NULL COMMENT '展示名；不是宿主路径',
    `media_type` VARCHAR(120) NOT NULL COMMENT '已验证媒体类型',
    `object_key` VARCHAR(240) NOT NULL COMMENT '服务端对象引用',
    `sha256` VARCHAR(64) NOT NULL COMMENT '文件摘要',
    `size_bytes` BIGINT NOT NULL DEFAULT 0 COMMENT '实际字节数',
    `status` VARCHAR(16) NOT NULL COMMENT 'STAGING/READY/DELETING/DELETED',
    `expires_at` DATETIME(3) NULL COMMENT '保留截止',
    `creator_id` VARCHAR(36) NOT NULL DEFAULT '' COMMENT '创建人ID',
    `creator_name` VARCHAR(50) NOT NULL DEFAULT '' COMMENT '创建人姓名',
    `create_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    `last_updater_id` VARCHAR(36) NOT NULL DEFAULT '' COMMENT '最后更新人ID',
    `last_updater_name` VARCHAR(50) NOT NULL DEFAULT '' COMMENT '最后更新人姓名',
    `last_update_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '最后更新时间',
    `del_flag` CHAR(1) NOT NULL DEFAULT '0' COMMENT '删除标志[0-否,1-是]',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_owner_artifact` (`user_id`, `artifact_id`),
    UNIQUE KEY `uk_object_key` (`object_key`),
    CONSTRAINT `ck_artifact_del` CHECK (`del_flag` IN ('0', '1'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='文件产物与上传生命周期';

CREATE TABLE `sq_task_event` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '存储主键ID',
    `user_id` CHAR(36) NOT NULL COMMENT '资源归属用户ID',
    `task_id` CHAR(36) NOT NULL COMMENT '目标ID',
    `event_seq` BIGINT NOT NULL DEFAULT 0 COMMENT '目标内递增序号',
    `producer_key` VARCHAR(200) NOT NULL COMMENT '稳定生产事件键',
    `event_type` VARCHAR(64) NOT NULL COMMENT 'TaskEvent类型',
    `answer_revision` BIGINT NOT NULL DEFAULT 0 COMMENT '正文版本',
    `payload_json` JSON NOT NULL COMMENT '脱敏公开事件载荷',
    `occurred_at` DATETIME(3) NOT NULL COMMENT '发生时间',
    `creator_id` VARCHAR(36) NOT NULL DEFAULT '' COMMENT '创建人ID',
    `creator_name` VARCHAR(50) NOT NULL DEFAULT '' COMMENT '创建人姓名',
    `create_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    `last_updater_id` VARCHAR(36) NOT NULL DEFAULT '' COMMENT '最后更新人ID',
    `last_updater_name` VARCHAR(50) NOT NULL DEFAULT '' COMMENT '最后更新人姓名',
    `last_update_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '最后更新时间',
    `del_flag` CHAR(1) NOT NULL DEFAULT '0' COMMENT '删除标志[0-否,1-是]',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_task_seq` (`user_id`, `task_id`, `event_seq`),
    UNIQUE KEY `uk_producer` (`user_id`, `task_id`, `producer_key`),
    CONSTRAINT `ck_task_event_del` CHECK (`del_flag` IN ('0', '1'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='用户可见持久事件';

CREATE TABLE `sq_projection_receipt` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '存储主键ID',
    `user_id` CHAR(36) NOT NULL COMMENT '资源归属用户ID',
    `binding_id` CHAR(36) NOT NULL COMMENT '框架绑定ID',
    `native_event_id` VARCHAR(200) NOT NULL COMMENT '原生事件ID',
    `native_seq` BIGINT NOT NULL DEFAULT 0 COMMENT '原生顺序',
    `payload_hash` VARCHAR(64) NOT NULL COMMENT '事件摘要；同ID换内容拒绝',
    `creator_id` VARCHAR(36) NOT NULL DEFAULT '' COMMENT '创建人ID',
    `creator_name` VARCHAR(50) NOT NULL DEFAULT '' COMMENT '创建人姓名',
    `create_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    `last_updater_id` VARCHAR(36) NOT NULL DEFAULT '' COMMENT '最后更新人ID',
    `last_updater_name` VARCHAR(50) NOT NULL DEFAULT '' COMMENT '最后更新人姓名',
    `last_update_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '最后更新时间',
    `del_flag` CHAR(1) NOT NULL DEFAULT '0' COMMENT '删除标志[0-否,1-是]',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_native_event` (`user_id`, `binding_id`, `native_event_id`),
    UNIQUE KEY `uk_native_seq` (`user_id`, `binding_id`, `native_seq`),
    CONSTRAINT `ck_projection_receipt_del` CHECK (`del_flag` IN ('0', '1'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='原生日志导出幂等回执';

CREATE TABLE `sq_budget_reservation` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '存储主键ID',
    `reservation_id` CHAR(36) NOT NULL COMMENT '预留ID',
    `user_id` CHAR(36) NOT NULL COMMENT '资源归属用户ID',
    `task_id` CHAR(36) NOT NULL COMMENT '目标ID',
    `usage_key` VARCHAR(200) NOT NULL COMMENT '模型或工具真实请求稳定键',
    `kind` VARCHAR(16) NOT NULL COMMENT 'MODEL/TOOL',
    `reserved_units` BIGINT NOT NULL DEFAULT 0 COMMENT '预留整数微单位',
    `settled_units` BIGINT NOT NULL DEFAULT 0 COMMENT '已结算整数微单位',
    `input_tokens` BIGINT NOT NULL DEFAULT 0 COMMENT '输入token累计',
    `output_tokens` BIGINT NOT NULL DEFAULT 0 COMMENT '输出token累计',
    `status` VARCHAR(16) NOT NULL COMMENT 'RESERVED/SETTLED/UNKNOWN',
    `version` BIGINT NOT NULL DEFAULT 0 COMMENT '结算版本',
    `creator_id` VARCHAR(36) NOT NULL DEFAULT '' COMMENT '创建人ID',
    `creator_name` VARCHAR(50) NOT NULL DEFAULT '' COMMENT '创建人姓名',
    `create_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    `last_updater_id` VARCHAR(36) NOT NULL DEFAULT '' COMMENT '最后更新人ID',
    `last_updater_name` VARCHAR(50) NOT NULL DEFAULT '' COMMENT '最后更新人姓名',
    `last_update_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '最后更新时间',
    `del_flag` CHAR(1) NOT NULL DEFAULT '0' COMMENT '删除标志[0-否,1-是]',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_reservation` (`reservation_id`),
    UNIQUE KEY `uk_usage` (`user_id`, `task_id`, `usage_key`),
    CONSTRAINT `ck_budget_reservation_del` CHECK (`del_flag` IN ('0', '1'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='全局预算预留及可信结算';

CREATE TABLE `sq_audit` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '存储主键ID',
    `audit_id` CHAR(36) NOT NULL COMMENT '审计ID',
    `user_id` CHAR(36) NOT NULL COMMENT '资源归属用户ID',
    `actor_id` VARCHAR(100) NOT NULL COMMENT '实际操作者或实例',
    `task_id` CHAR(36) NULL COMMENT '目标ID',
    `operation_id` CHAR(36) NULL COMMENT '操作ID',
    `action` VARCHAR(100) NOT NULL COMMENT '审计动作',
    `result_code` VARCHAR(80) NOT NULL COMMENT '结果编码',
    `trace_id` VARCHAR(64) NOT NULL COMMENT '链路标识',
    `details_json` JSON NOT NULL COMMENT '脱敏审计详情；不得放密钥和思考链',
    `creator_id` VARCHAR(36) NOT NULL DEFAULT '' COMMENT '创建人ID',
    `creator_name` VARCHAR(50) NOT NULL DEFAULT '' COMMENT '创建人姓名',
    `create_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    `last_updater_id` VARCHAR(36) NOT NULL DEFAULT '' COMMENT '最后更新人ID',
    `last_updater_name` VARCHAR(50) NOT NULL DEFAULT '' COMMENT '最后更新人姓名',
    `last_update_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '最后更新时间',
    `del_flag` CHAR(1) NOT NULL DEFAULT '0' COMMENT '删除标志[0-否,1-是]',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_audit` (`audit_id`),
    CONSTRAINT `ck_audit_del` CHECK (`del_flag` IN ('0', '1'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='安全与业务审计';

ALTER TABLE `sq_auth_session` ADD CONSTRAINT `fk_auth_session_1` FOREIGN KEY (`user_id`) REFERENCES `sq_account` (`user_id`) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE `sq_conversation` ADD CONSTRAINT `fk_conversation_2` FOREIGN KEY (`user_id`) REFERENCES `sq_account` (`user_id`) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE `sq_goal_task` ADD CONSTRAINT `fk_goal_task_3` FOREIGN KEY (`user_id`, `conversation_id`) REFERENCES `sq_conversation` (`user_id`, `conversation_id`) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE `sq_plan` ADD CONSTRAINT `fk_plan_4` FOREIGN KEY (`user_id`, `task_id`) REFERENCES `sq_goal_task` (`user_id`, `task_id`) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE `sq_worker_job` ADD CONSTRAINT `fk_worker_job_5` FOREIGN KEY (`user_id`, `task_id`) REFERENCES `sq_goal_task` (`user_id`, `task_id`) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE `sq_runtime_binding` ADD CONSTRAINT `fk_runtime_binding_6` FOREIGN KEY (`user_id`, `task_id`) REFERENCES `sq_goal_task` (`user_id`, `task_id`) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE `sq_context_snapshot` ADD CONSTRAINT `fk_context_snapshot_7` FOREIGN KEY (`user_id`, `task_id`) REFERENCES `sq_goal_task` (`user_id`, `task_id`) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE `sq_operation` ADD CONSTRAINT `fk_operation_8` FOREIGN KEY (`user_id`, `task_id`) REFERENCES `sq_goal_task` (`user_id`, `task_id`) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE `sq_interaction` ADD CONSTRAINT `fk_interaction_9` FOREIGN KEY (`user_id`, `task_id`) REFERENCES `sq_goal_task` (`user_id`, `task_id`) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE `sq_task_event` ADD CONSTRAINT `fk_task_event_10` FOREIGN KEY (`user_id`, `task_id`) REFERENCES `sq_goal_task` (`user_id`, `task_id`) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE `sq_budget_reservation` ADD CONSTRAINT `fk_budget_reservation_11` FOREIGN KEY (`user_id`, `task_id`) REFERENCES `sq_goal_task` (`user_id`, `task_id`) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE `sq_worker_job` ADD CONSTRAINT `fk_worker_job_12` FOREIGN KEY (`user_id`, `task_id`, `plan_version`) REFERENCES `sq_plan` (`user_id`, `task_id`, `plan_version`) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE `sq_worker_job` ADD CONSTRAINT `fk_worker_job_13` FOREIGN KEY (`user_id`, `context_snapshot_id`) REFERENCES `sq_context_snapshot` (`user_id`, `snapshot_id`) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE `sq_worker_job` ADD CONSTRAINT `fk_worker_job_14` FOREIGN KEY (`worker_key`, `worker_version`) REFERENCES `sq_worker_definition` (`worker_key`, `worker_version`) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE `sq_skill_grant` ADD CONSTRAINT `fk_skill_grant_15` FOREIGN KEY (`user_id`, `skill_version_id`) REFERENCES `sq_skill_version` (`user_id`, `skill_version_id`) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE `sq_operation` ADD CONSTRAINT `fk_operation_16` FOREIGN KEY (`user_id`, `binding_id`) REFERENCES `sq_runtime_binding` (`user_id`, `binding_id`) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE `sq_operation` ADD CONSTRAINT `fk_operation_17` FOREIGN KEY (`user_id`, `connection_id`) REFERENCES `sq_connection` (`user_id`, `connection_id`) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE `sq_interaction` ADD CONSTRAINT `fk_interaction_18` FOREIGN KEY (`user_id`, `operation_id`) REFERENCES `sq_operation` (`user_id`, `operation_id`) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE `sq_projection_receipt` ADD CONSTRAINT `fk_projection_receipt_19` FOREIGN KEY (`user_id`, `binding_id`) REFERENCES `sq_runtime_binding` (`user_id`, `binding_id`) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE `sq_memory` ADD CONSTRAINT `fk_memory_20` FOREIGN KEY (`user_id`) REFERENCES `sq_account` (`user_id`) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE `sq_connection` ADD CONSTRAINT `fk_connection_21` FOREIGN KEY (`user_id`) REFERENCES `sq_account` (`user_id`) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE `sq_skill_version` ADD CONSTRAINT `fk_skill_version_22` FOREIGN KEY (`user_id`) REFERENCES `sq_account` (`user_id`) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE `sq_artifact` ADD CONSTRAINT `fk_artifact_23` FOREIGN KEY (`user_id`) REFERENCES `sq_account` (`user_id`) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE `sq_command` ADD CONSTRAINT `fk_command_24` FOREIGN KEY (`user_id`) REFERENCES `sq_account` (`user_id`) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE `sq_audit` ADD CONSTRAINT `fk_audit_25` FOREIGN KEY (`user_id`) REFERENCES `sq_account` (`user_id`) ON DELETE RESTRICT ON UPDATE RESTRICT;
