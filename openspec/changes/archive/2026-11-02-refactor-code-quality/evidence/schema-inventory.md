# 最终存储结构合并清单

2026-10-30。来源：仓库 V1–V18 SQL 与 Java 回填迁移；只读核对隔离容器 `agentsquad-d45-validation-20261030` 的已完成 V18 测试 schema `as_test_00e019b6f964fc38eb1773e8`。MySQL 客户端显式使用 `utf8mb4` 导出，中文注释已核对。未修改迁移、未创建或清理数据库。

Flyway 成功版本：1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18。业务及框架表共 44 张；不含 Flyway 历史表。

## 迁移来源

| 文件 | 新建表 | 修改表 | 包含 DML |
|---|---|---|---|
| V1__baseline.sql | sq_conversation, sq_goal_task, sq_runtime_binding, sq_command, sq_task_event, sq_connection, sq_secret_vault, sq_operation, sq_budget_reservation, sq_mock_ticket, sq_interaction, sq_worker_definition, agentscope_store, agentscope_sessions, agentscope_snapshots |  | False |
| V2__plan_worker_job.sql | sq_plan, sq_worker_job |  | False |
| V3__context_snapshot.sql | sq_context_snapshot | sq_worker_job | False |
| V4__task_budget_profiles.sql |  | sq_goal_task, sq_budget_reservation | False |
| V5__worker_guard.sql |  | sq_worker_job | False |
| V6__artifact_storage.sql | sq_artifact, sq_task_artifact, sq_operation_reconciler |  | False |
| V7__conversation_execution_foundation.sql | sq_budget_account, sq_execution_scope, sq_message, sq_response_execution, sq_conversation_event, sq_message_artifact | sq_conversation, sq_goal_task, sq_runtime_binding, sq_command, sq_operation, sq_interaction, sq_context_snapshot, sq_budget_reservation | False |
| V9__scope_runtime_ownership.sql | sq_user_budget, sq_scope_artifact | sq_runtime_binding, sq_operation, sq_interaction, sq_budget_reservation, sq_operation_reconciler | True |
| V10__operation_budget_identity.sql |  | sq_operation | True |
| V11__response_context_prefix.sql |  | sq_response_execution | False |
| V12__task_directive_governance.sql | sq_task_directive | sq_goal_task, sq_context_snapshot, sq_plan, sq_runtime_binding | False |
| V13__execution_failure_recovery.sql | sq_execution_failure | sq_execution_scope, sq_response_execution, sq_conversation | True |
| V15__skill_governance.sql | sq_skill_version, sq_skill_file, sq_skill_grant, sq_scope_skill_catalog, sq_scope_skill_ref |  | False |
| V16__explicit_memory.sql | sq_memory, sq_memory_revision, sq_memory_scope, sq_memory_scope_item, sq_memory_write_receipt, sq_memory_source |  | False |
| V17__sandbox_delivery.sql | sq_sandbox_execution, sq_artifact_stage |  | False |
| V18__memory_active_key.sql |  | sq_memory | False |

## agentscope_sessions

审计字段：非统一七字段（框架表或需另行核查）。

| 列 | 类型 | 可空 | 默认值 | 扩展 | 注释 |
|---|---|---|---|---|---|
| session_id | varchar(255) | NO | NULL |  | 框架会话标识 |
| state_key | varchar(255) | NO | NULL |  | 框架状态键 |
| item_index | int | NO | 0 |  | 状态项序号 |
| state_data | longtext | NO | NULL |  | 框架状态内容 |
| version | bigint | NO | 0 |  | 状态 CAS 版本 |
| created_at | datetime | YES | CURRENT_TIMESTAMP | DEFAULT_GENERATED | 创建时间 |
| updated_at | datetime | YES | CURRENT_TIMESTAMP | DEFAULT_GENERATED on update CURRENT_TIMESTAMP | 更新时间 |

索引、外键与完整建表定义（SHOW CREATE TABLE；仅结构）：

```sql
CREATE TABLE `agentscope_sessions` (
  `session_id` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '框架会话标识',
  `state_key` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '框架状态键',
  `item_index` int NOT NULL DEFAULT '0' COMMENT '状态项序号',
  `state_data` longtext COLLATE utf8mb4_bin NOT NULL COMMENT '框架状态内容',
  `version` bigint NOT NULL DEFAULT '0' COMMENT '状态 CAS 版本',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`session_id`,`state_key`,`item_index`),
  KEY `idx_agentscope_session` (`session_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='AgentScope 会话状态';
```

## agentscope_snapshots

审计字段：非统一七字段（框架表或需另行核查）。

| 列 | 类型 | 可空 | 默认值 | 扩展 | 注释 |
|---|---|---|---|---|---|
| snapshot_id | varchar(512) | NO | NULL |  | 框架沙箱快照标识 |
| data | longblob | NO | NULL |  | 沙箱快照字节 |
| created_at | timestamp | YES | CURRENT_TIMESTAMP | DEFAULT_GENERATED | 创建时间 |

索引、外键与完整建表定义（SHOW CREATE TABLE；仅结构）：

```sql
CREATE TABLE `agentscope_snapshots` (
  `snapshot_id` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '框架沙箱快照标识',
  `data` longblob NOT NULL COMMENT '沙箱快照字节',
  `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`snapshot_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='AgentScope 沙箱快照';
```

## agentscope_store

审计字段：非统一七字段（框架表或需另行核查）。

| 列 | 类型 | 可空 | 默认值 | 扩展 | 注释 |
|---|---|---|---|---|---|
| namespace_path | varchar(255) | NO | NULL |  | AgentScope 用户隔离与存储分区 |
| item_key | varchar(512) | NO | NULL |  | Workspace 与原生日志对象路径 |
| value_json | longtext | NO | NULL |  | 框架原生存储对象 |
| version | bigint | NO | NULL |  | 框架 CAS 版本 |
| updated_at | bigint | NO | NULL |  | 框架更新时间戳 |

索引、外键与完整建表定义（SHOW CREATE TABLE；仅结构）：

```sql
CREATE TABLE `agentscope_store` (
  `namespace_path` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT 'AgentScope 用户隔离与存储分区',
  `item_key` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT 'Workspace 与原生日志对象路径',
  `value_json` longtext COLLATE utf8mb4_bin NOT NULL COMMENT '框架原生存储对象',
  `version` bigint NOT NULL COMMENT '框架 CAS 版本',
  `updated_at` bigint NOT NULL COMMENT '框架更新时间戳',
  PRIMARY KEY (`namespace_path`,`item_key`),
  KEY `idx_agentscope_namespace` (`namespace_path`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='AgentScope JDBC 共享存储';
```

## sq_artifact

审计字段：完整七字段。

| 列 | 类型 | 可空 | 默认值 | 扩展 | 注释 |
|---|---|---|---|---|---|
| id | bigint | NO | NULL | auto_increment | 存储主键ID |
| artifact_id | char(36) | NO | NULL |  | 产物业务ID |
| user_id | char(36) | NO | NULL |  | 归属用户ID |
| file_name | varchar(240) | NO | NULL |  | 安全展示文件名 |
| media_type | varchar(120) | NO | NULL |  | 服务端判定媒体类型 |
| size_bytes | bigint | NO | NULL |  | 内容字节数 |
| sha256 | char(64) | NO | NULL |  | 内容SHA256摘要 |
| upload_key | varchar(160) | NO | NULL |  | 归属用户内的上传幂等键 |
| content_bytes | longblob | NO | NULL |  | 已校验的小文件内容 |
| expires_at | datetime(3) | NO | NULL |  | 读取有效期 |
| creator_id | varchar(36) | NO |  |  | 创建人ID |
| creator_name | varchar(50) | NO |  |  | 创建人姓名 |
| create_time | datetime(3) | NO | CURRENT_TIMESTAMP(3) | DEFAULT_GENERATED | 创建时间 |
| last_updater_id | varchar(36) | NO |  |  | 最后更新人ID |
| last_updater_name | varchar(50) | NO |  |  | 最后更新人姓名 |
| last_update_time | datetime(3) | NO | CURRENT_TIMESTAMP(3) | DEFAULT_GENERATED on update CURRENT_TIMESTAMP(3) | 最后更新时间 |
| del_flag | char(1) | NO | 0 |  | 删除标志[0-否,1-是] |

索引、外键与完整建表定义（SHOW CREATE TABLE；仅结构）：

```sql
CREATE TABLE `sq_artifact` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '存储主键ID',
  `artifact_id` char(36) COLLATE utf8mb4_bin NOT NULL COMMENT '产物业务ID',
  `user_id` char(36) COLLATE utf8mb4_bin NOT NULL COMMENT '归属用户ID',
  `file_name` varchar(240) COLLATE utf8mb4_bin NOT NULL COMMENT '安全展示文件名',
  `media_type` varchar(120) COLLATE utf8mb4_bin NOT NULL COMMENT '服务端判定媒体类型',
  `size_bytes` bigint NOT NULL COMMENT '内容字节数',
  `sha256` char(64) COLLATE utf8mb4_bin NOT NULL COMMENT '内容SHA256摘要',
  `upload_key` varchar(160) COLLATE utf8mb4_bin NOT NULL COMMENT '归属用户内的上传幂等键',
  `content_bytes` longblob NOT NULL COMMENT '已校验的小文件内容',
  `expires_at` datetime(3) NOT NULL COMMENT '读取有效期',
  `creator_id` varchar(36) COLLATE utf8mb4_bin NOT NULL DEFAULT '' COMMENT '创建人ID',
  `creator_name` varchar(50) COLLATE utf8mb4_bin NOT NULL DEFAULT '' COMMENT '创建人姓名',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `last_updater_id` varchar(36) COLLATE utf8mb4_bin NOT NULL DEFAULT '' COMMENT '最后更新人ID',
  `last_updater_name` varchar(50) COLLATE utf8mb4_bin NOT NULL DEFAULT '' COMMENT '最后更新人姓名',
  `last_update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '最后更新时间',
  `del_flag` char(1) COLLATE utf8mb4_bin NOT NULL DEFAULT '0' COMMENT '删除标志[0-否,1-是]',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_artifact` (`artifact_id`),
  UNIQUE KEY `uk_artifact_owner` (`user_id`,`artifact_id`),
  UNIQUE KEY `uk_artifact_upload` (`user_id`,`upload_key`),
  CONSTRAINT `ck_artifact_size` CHECK (((`size_bytes` >= 0) and (`size_bytes` <= 10485760)))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='小文件产物';
```

## sq_artifact_stage

审计字段：完整七字段。

| 列 | 类型 | 可空 | 默认值 | 扩展 | 注释 |
|---|---|---|---|---|---|
| id | bigint | NO | NULL | auto_increment | 存储主键ID |
| stage_id | char(36) | NO | NULL |  | 暂存产物业务ID |
| user_id | char(36) | NO | NULL |  | 归属用户ID |
| scope_id | char(36) | NO | NULL |  | 交付作用域ID |
| binding_id | char(36) | NO | NULL |  | 来源绑定ID |
| execution_id | char(36) | NO | NULL |  | 来源沙箱执行ID |
| workspace_key | varchar(200) | NO | NULL |  | 来源隔离工作区相对路径 |
| file_name | varchar(240) | NO | NULL |  | 安全展示文件名 |
| sha256 | char(64) | NO | NULL |  | 固定文件SHA256摘要 |
| content_bytes | longblob | NO | NULL |  | 未发布暂存内容 |
| status | varchar(20) | NO | NULL |  | 状态[STAGED,COMMITTED,EXPIRED] |
| artifact_id | char(36) | YES | NULL |  | 已发布产物ID |
| expires_at | datetime(3) | NO | NULL |  | 暂存保留截止时间 |
| creator_id | varchar(36) | NO |  |  | 创建人ID |
| creator_name | varchar(50) | NO |  |  | 创建人姓名 |
| create_time | datetime(3) | NO | CURRENT_TIMESTAMP(3) | DEFAULT_GENERATED | 创建时间 |
| last_updater_id | varchar(36) | NO |  |  | 最后更新人ID |
| last_updater_name | varchar(50) | NO |  |  | 最后更新人姓名 |
| last_update_time | datetime(3) | NO | CURRENT_TIMESTAMP(3) | DEFAULT_GENERATED on update CURRENT_TIMESTAMP(3) | 最后更新时间 |
| del_flag | char(1) | NO | 0 |  | 删除标志[0-否,1-是] |

索引、外键与完整建表定义（SHOW CREATE TABLE；仅结构）：

```sql
CREATE TABLE `sq_artifact_stage` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '存储主键ID',
  `stage_id` char(36) COLLATE utf8mb4_bin NOT NULL COMMENT '暂存产物业务ID',
  `user_id` char(36) COLLATE utf8mb4_bin NOT NULL COMMENT '归属用户ID',
  `scope_id` char(36) COLLATE utf8mb4_bin NOT NULL COMMENT '交付作用域ID',
  `binding_id` char(36) COLLATE utf8mb4_bin NOT NULL COMMENT '来源绑定ID',
  `execution_id` char(36) COLLATE utf8mb4_bin NOT NULL COMMENT '来源沙箱执行ID',
  `workspace_key` varchar(200) COLLATE utf8mb4_bin NOT NULL COMMENT '来源隔离工作区相对路径',
  `file_name` varchar(240) COLLATE utf8mb4_bin NOT NULL COMMENT '安全展示文件名',
  `sha256` char(64) COLLATE utf8mb4_bin NOT NULL COMMENT '固定文件SHA256摘要',
  `content_bytes` longblob NOT NULL COMMENT '未发布暂存内容',
  `status` varchar(20) COLLATE utf8mb4_bin NOT NULL COMMENT '状态[STAGED,COMMITTED,EXPIRED]',
  `artifact_id` char(36) COLLATE utf8mb4_bin DEFAULT NULL COMMENT '已发布产物ID',
  `expires_at` datetime(3) NOT NULL COMMENT '暂存保留截止时间',
  `creator_id` varchar(36) COLLATE utf8mb4_bin NOT NULL DEFAULT '' COMMENT '创建人ID',
  `creator_name` varchar(50) COLLATE utf8mb4_bin NOT NULL DEFAULT '' COMMENT '创建人姓名',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `last_updater_id` varchar(36) COLLATE utf8mb4_bin NOT NULL DEFAULT '' COMMENT '最后更新人ID',
  `last_updater_name` varchar(50) COLLATE utf8mb4_bin NOT NULL DEFAULT '' COMMENT '最后更新人姓名',
  `last_update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '最后更新时间',
  `del_flag` char(1) COLLATE utf8mb4_bin NOT NULL DEFAULT '0' COMMENT '删除标志[0-否,1-是]',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_artifact_stage` (`user_id`,`stage_id`),
  UNIQUE KEY `uk_artifact_stage_path` (`user_id`,`execution_id`,`workspace_key`),
  KEY `fk_stage_scope` (`user_id`,`scope_id`),
  KEY `fk_stage_binding` (`user_id`,`binding_id`),
  KEY `fk_stage_artifact` (`user_id`,`artifact_id`),
  CONSTRAINT `fk_stage_artifact` FOREIGN KEY (`user_id`, `artifact_id`) REFERENCES `sq_artifact` (`user_id`, `artifact_id`),
  CONSTRAINT `fk_stage_binding` FOREIGN KEY (`user_id`, `binding_id`) REFERENCES `sq_runtime_binding` (`user_id`, `binding_id`),
  CONSTRAINT `fk_stage_execution` FOREIGN KEY (`user_id`, `execution_id`) REFERENCES `sq_sandbox_execution` (`user_id`, `execution_id`),
  CONSTRAINT `fk_stage_scope` FOREIGN KEY (`user_id`, `scope_id`) REFERENCES `sq_execution_scope` (`user_id`, `scope_id`),
  CONSTRAINT `ck_artifact_stage_status` CHECK ((`status` in (_utf8mb4'STAGED',_utf8mb4'COMMITTED',_utf8mb4'EXPIRED')))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='隔离产物受控暂存';
```

## sq_budget_account

审计字段：完整七字段。

| 列 | 类型 | 可空 | 默认值 | 扩展 | 注释 |
|---|---|---|---|---|---|
| id | bigint | NO | NULL | auto_increment | 存储主键ID |
| account_id | char(36) | NO | NULL |  | 预算账户业务ID |
| user_id | char(36) | NO | NULL |  | 资源归属用户ID |
| limit_microunits | bigint | NO | NULL |  | 固定共享预算上限，整数微单位 |
| profile_key | varchar(80) | NO | NULL |  | 固定预算Profile键 |
| status | varchar(16) | NO | NULL |  | 状态[OPEN,CLOSED] |
| version | bigint | NO | 0 |  | 业务乐观版本 |
| creator_id | varchar(36) | NO |  |  | 创建人ID |
| creator_name | varchar(50) | NO |  |  | 创建人姓名 |
| create_time | datetime(3) | NO | CURRENT_TIMESTAMP(3) | DEFAULT_GENERATED | 创建时间 |
| last_updater_id | varchar(36) | NO |  |  | 最后更新人ID |
| last_updater_name | varchar(50) | NO |  |  | 最后更新人姓名 |
| last_update_time | datetime(3) | NO | CURRENT_TIMESTAMP(3) | DEFAULT_GENERATED on update CURRENT_TIMESTAMP(3) | 最后更新时间 |
| del_flag | char(1) | NO | 0 |  | 删除标志[0-否,1-是] |

索引、外键与完整建表定义（SHOW CREATE TABLE；仅结构）：

```sql
CREATE TABLE `sq_budget_account` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '存储主键ID',
  `account_id` char(36) COLLATE utf8mb4_bin NOT NULL COMMENT '预算账户业务ID',
  `user_id` char(36) COLLATE utf8mb4_bin NOT NULL COMMENT '资源归属用户ID',
  `limit_microunits` bigint NOT NULL COMMENT '固定共享预算上限，整数微单位',
  `profile_key` varchar(80) COLLATE utf8mb4_bin NOT NULL COMMENT '固定预算Profile键',
  `status` varchar(16) COLLATE utf8mb4_bin NOT NULL COMMENT '状态[OPEN,CLOSED]',
  `version` bigint NOT NULL DEFAULT '0' COMMENT '业务乐观版本',
  `creator_id` varchar(36) COLLATE utf8mb4_bin NOT NULL DEFAULT '' COMMENT '创建人ID',
  `creator_name` varchar(50) COLLATE utf8mb4_bin NOT NULL DEFAULT '' COMMENT '创建人姓名',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `last_updater_id` varchar(36) COLLATE utf8mb4_bin NOT NULL DEFAULT '' COMMENT '最后更新人ID',
  `last_updater_name` varchar(50) COLLATE utf8mb4_bin NOT NULL DEFAULT '' COMMENT '最后更新人姓名',
  `last_update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '最后更新时间',
  `del_flag` char(1) COLLATE utf8mb4_bin NOT NULL DEFAULT '0' COMMENT '删除标志[0-否,1-是]',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_budget_account` (`user_id`,`account_id`),
  CONSTRAINT `ck_budget_account_del` CHECK ((`del_flag` in (_utf8mb4'0',_utf8mb4'1'))),
  CONSTRAINT `ck_budget_account_limit` CHECK ((`limit_microunits` > 0)),
  CONSTRAINT `ck_budget_account_status` CHECK ((`status` in (_utf8mb4'OPEN',_utf8mb4'CLOSED')))
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='回复与任务共享预算账户';
```

## sq_budget_reservation

审计字段：完整七字段。

| 列 | 类型 | 可空 | 默认值 | 扩展 | 注释 |
|---|---|---|---|---|---|
| id | bigint | NO | NULL | auto_increment | 存储主键ID |
| reservation_id | char(36) | NO | NULL |  | 预留ID |
| user_id | char(36) | NO | NULL |  | 资源归属用户ID |
| task_id | char(36) | YES | NULL |  | 可选目标上下文ID；预算以Scope和账户为准 |
| usage_key | varchar(200) | NO | NULL |  | 模型或工具真实请求稳定键 |
| kind | varchar(16) | NO | NULL |  | MODEL/TOOL |
| reserved_units | bigint | NO | 0 |  | 预留整数微单位 |
| settled_units | bigint | NO | 0 |  | 已结算整数微单位 |
| input_tokens | bigint | NO | 0 |  | 输入token累计 |
| output_tokens | bigint | NO | 0 |  | 输出token累计 |
| status | varchar(16) | NO | NULL |  | RESERVED/SETTLED/UNKNOWN |
| version | bigint | NO | 0 |  | 结算版本 |
| creator_id | varchar(36) | NO |  |  | 创建人ID |
| creator_name | varchar(50) | NO |  |  | 创建人姓名 |
| create_time | datetime(3) | NO | CURRENT_TIMESTAMP(3) | DEFAULT_GENERATED | 创建时间 |
| last_updater_id | varchar(36) | NO |  |  | 最后更新人ID |
| last_updater_name | varchar(50) | NO |  |  | 最后更新人姓名 |
| last_update_time | datetime(3) | NO | CURRENT_TIMESTAMP(3) | DEFAULT_GENERATED on update CURRENT_TIMESTAMP(3) | 最后更新时间 |
| del_flag | char(1) | NO | 0 |  | 删除标志[0-否,1-是] |
| model_profile_key | varchar(80) | YES | NULL |  | 模型请求使用的服务端Profile键 |
| pricing_version | varchar(80) | YES | NULL |  | 模型定价版本，结算时不得切换 |
| request_hash | char(64) | YES | NULL |  | 模型请求输入规范化摘要，不保存完整Prompt |
| scope_id | char(36) | YES | NULL |  | 真实请求执行作用域ID |
| account_id | char(36) | YES | NULL |  | 共享预算账户ID |

索引、外键与完整建表定义（SHOW CREATE TABLE；仅结构）：

```sql
CREATE TABLE `sq_budget_reservation` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '存储主键ID',
  `reservation_id` char(36) COLLATE utf8mb4_bin NOT NULL COMMENT '预留ID',
  `user_id` char(36) COLLATE utf8mb4_bin NOT NULL COMMENT '资源归属用户ID',
  `task_id` char(36) COLLATE utf8mb4_bin DEFAULT NULL COMMENT '可选目标上下文ID；预算以Scope和账户为准',
  `usage_key` varchar(200) COLLATE utf8mb4_bin NOT NULL COMMENT '模型或工具真实请求稳定键',
  `kind` varchar(16) COLLATE utf8mb4_bin NOT NULL COMMENT 'MODEL/TOOL',
  `reserved_units` bigint NOT NULL DEFAULT '0' COMMENT '预留整数微单位',
  `settled_units` bigint NOT NULL DEFAULT '0' COMMENT '已结算整数微单位',
  `input_tokens` bigint NOT NULL DEFAULT '0' COMMENT '输入token累计',
  `output_tokens` bigint NOT NULL DEFAULT '0' COMMENT '输出token累计',
  `status` varchar(16) COLLATE utf8mb4_bin NOT NULL COMMENT 'RESERVED/SETTLED/UNKNOWN',
  `version` bigint NOT NULL DEFAULT '0' COMMENT '结算版本',
  `creator_id` varchar(36) COLLATE utf8mb4_bin NOT NULL DEFAULT '' COMMENT '创建人ID',
  `creator_name` varchar(50) COLLATE utf8mb4_bin NOT NULL DEFAULT '' COMMENT '创建人姓名',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `last_updater_id` varchar(36) COLLATE utf8mb4_bin NOT NULL DEFAULT '' COMMENT '最后更新人ID',
  `last_updater_name` varchar(50) COLLATE utf8mb4_bin NOT NULL DEFAULT '' COMMENT '最后更新人姓名',
  `last_update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '最后更新时间',
  `del_flag` char(1) COLLATE utf8mb4_bin NOT NULL DEFAULT '0' COMMENT '删除标志[0-否,1-是]',
  `model_profile_key` varchar(80) COLLATE utf8mb4_bin DEFAULT NULL COMMENT '模型请求使用的服务端Profile键',
  `pricing_version` varchar(80) COLLATE utf8mb4_bin DEFAULT NULL COMMENT '模型定价版本，结算时不得切换',
  `request_hash` char(64) COLLATE utf8mb4_bin DEFAULT NULL COMMENT '模型请求输入规范化摘要，不保存完整Prompt',
  `scope_id` char(36) COLLATE utf8mb4_bin DEFAULT NULL COMMENT '真实请求执行作用域ID',
  `account_id` char(36) COLLATE utf8mb4_bin DEFAULT NULL COMMENT '共享预算账户ID',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_reservation` (`reservation_id`),
  UNIQUE KEY `uk_usage` (`user_id`,`task_id`,`usage_key`),
  UNIQUE KEY `uk_scope_usage` (`user_id`,`scope_id`,`usage_key`),
  KEY `fk_reservation_account` (`user_id`,`account_id`),
  CONSTRAINT `fk_reservation_account` FOREIGN KEY (`user_id`, `account_id`) REFERENCES `sq_budget_account` (`user_id`, `account_id`),
  CONSTRAINT `fk_reservation_scope` FOREIGN KEY (`user_id`, `scope_id`) REFERENCES `sq_execution_scope` (`user_id`, `scope_id`),
  CONSTRAINT `ck_budget_reservation_del` CHECK ((`del_flag` in (_utf8mb4'0',_utf8mb4'1')))
) ENGINE=InnoDB AUTO_INCREMENT=15 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='全局预算预留及可信结算';
```

## sq_command

审计字段：完整七字段。

| 列 | 类型 | 可空 | 默认值 | 扩展 | 注释 |
|---|---|---|---|---|---|
| id | bigint | NO | NULL | auto_increment | 存储主键ID |
| command_id | char(36) | NO | NULL |  | 命令业务ID |
| user_id | char(36) | NO | NULL |  | 资源归属用户ID |
| scope_key | varchar(160) | NO | NULL |  | 动作和资源幂等范围 |
| idempotency_key | varchar(160) | NO | NULL |  | 调用方稳定键 |
| payload_hash | char(64) | NO | NULL |  | 规范化请求SHA256 |
| task_id | char(36) | YES | NULL |  | 所属目标ID；删除类命令可为空 |
| binding_id | char(36) | YES | NULL |  | 原生会话绑定ID；删除类命令可为空 |
| action | varchar(32) | NO | NULL |  | 命令动作 |
| payload_json | json | NO | NULL |  | 规范化命令输入 |
| status | varchar(20) | NO | NULL |  | PENDING/DELIVERING/DELIVERED/APPLIED/BLOCKED |
| due_at | datetime(3) | NO | NULL |  | 下次投递时间 |
| delivery_token | char(36) | YES | NULL |  | 当前投递领取令牌 |
| delivery_lease_until | datetime(3) | YES | NULL |  | 投递令牌过期时间 |
| delivery_attempts | int | NO | 0 |  | 投递尝试次数 |
| native_turn_id | varchar(64) | YES | NULL |  | AgentScope 原生回合ID |
| native_run_id | varchar(64) | YES | NULL |  | AgentScope 原生运行ID |
| native_status | varchar(24) | YES | NULL |  | AgentScope 提交时状态 |
| last_error_code | varchar(64) | YES | NULL |  | 投递错误分类，不存异常详情 |
| creator_id | varchar(36) | NO |  |  | 创建人ID |
| creator_name | varchar(50) | NO |  |  | 创建人姓名 |
| create_time | datetime(3) | NO | CURRENT_TIMESTAMP(3) | DEFAULT_GENERATED | 创建时间 |
| last_updater_id | varchar(36) | NO |  |  | 最后更新人ID |
| last_updater_name | varchar(50) | NO |  |  | 最后更新人姓名 |
| last_update_time | datetime(3) | NO | CURRENT_TIMESTAMP(3) | DEFAULT_GENERATED on update CURRENT_TIMESTAMP(3) | 最后更新时间 |
| del_flag | char(1) | NO | 0 |  | 删除标志[0-否,1-是] |
| scope_id | char(36) | YES | NULL |  | 统一执行作用域ID |

索引、外键与完整建表定义（SHOW CREATE TABLE；仅结构）：

```sql
CREATE TABLE `sq_command` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '存储主键ID',
  `command_id` char(36) COLLATE utf8mb4_bin NOT NULL COMMENT '命令业务ID',
  `user_id` char(36) COLLATE utf8mb4_bin NOT NULL COMMENT '资源归属用户ID',
  `scope_key` varchar(160) COLLATE utf8mb4_bin NOT NULL COMMENT '动作和资源幂等范围',
  `idempotency_key` varchar(160) COLLATE utf8mb4_bin NOT NULL COMMENT '调用方稳定键',
  `payload_hash` char(64) COLLATE utf8mb4_bin NOT NULL COMMENT '规范化请求SHA256',
  `task_id` char(36) COLLATE utf8mb4_bin DEFAULT NULL COMMENT '所属目标ID；删除类命令可为空',
  `binding_id` char(36) COLLATE utf8mb4_bin DEFAULT NULL COMMENT '原生会话绑定ID；删除类命令可为空',
  `action` varchar(32) COLLATE utf8mb4_bin NOT NULL COMMENT '命令动作',
  `payload_json` json NOT NULL COMMENT '规范化命令输入',
  `status` varchar(20) COLLATE utf8mb4_bin NOT NULL COMMENT 'PENDING/DELIVERING/DELIVERED/APPLIED/BLOCKED',
  `due_at` datetime(3) NOT NULL COMMENT '下次投递时间',
  `delivery_token` char(36) COLLATE utf8mb4_bin DEFAULT NULL COMMENT '当前投递领取令牌',
  `delivery_lease_until` datetime(3) DEFAULT NULL COMMENT '投递令牌过期时间',
  `delivery_attempts` int NOT NULL DEFAULT '0' COMMENT '投递尝试次数',
  `native_turn_id` varchar(64) COLLATE utf8mb4_bin DEFAULT NULL COMMENT 'AgentScope 原生回合ID',
  `native_run_id` varchar(64) COLLATE utf8mb4_bin DEFAULT NULL COMMENT 'AgentScope 原生运行ID',
  `native_status` varchar(24) COLLATE utf8mb4_bin DEFAULT NULL COMMENT 'AgentScope 提交时状态',
  `last_error_code` varchar(64) COLLATE utf8mb4_bin DEFAULT NULL COMMENT '投递错误分类，不存异常详情',
  `creator_id` varchar(36) COLLATE utf8mb4_bin NOT NULL DEFAULT '' COMMENT '创建人ID',
  `creator_name` varchar(50) COLLATE utf8mb4_bin NOT NULL DEFAULT '' COMMENT '创建人姓名',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `last_updater_id` varchar(36) COLLATE utf8mb4_bin NOT NULL DEFAULT '' COMMENT '最后更新人ID',
  `last_updater_name` varchar(50) COLLATE utf8mb4_bin NOT NULL DEFAULT '' COMMENT '最后更新人姓名',
  `last_update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '最后更新时间',
  `del_flag` char(1) COLLATE utf8mb4_bin NOT NULL DEFAULT '0' COMMENT '删除标志[0-否,1-是]',
  `scope_id` char(36) COLLATE utf8mb4_bin DEFAULT NULL COMMENT '统一执行作用域ID',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_command` (`command_id`),
  UNIQUE KEY `uk_owner_scope_key` (`user_id`,`scope_key`,`idempotency_key`),
  KEY `idx_command_due` (`status`,`due_at`),
  KEY `fk_command_task` (`user_id`,`task_id`),
  KEY `fk_command_scope` (`user_id`,`scope_id`),
  CONSTRAINT `fk_command_scope` FOREIGN KEY (`user_id`, `scope_id`) REFERENCES `sq_execution_scope` (`user_id`, `scope_id`),
  CONSTRAINT `fk_command_task` FOREIGN KEY (`user_id`, `task_id`) REFERENCES `sq_goal_task` (`user_id`, `task_id`),
  CONSTRAINT `ck_command_del` CHECK ((`del_flag` in (_utf8mb4'0',_utf8mb4'1')))
) ENGINE=InnoDB AUTO_INCREMENT=6 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='持久命令与幂等回执';
```

## sq_connection

审计字段：完整七字段。

| 列 | 类型 | 可空 | 默认值 | 扩展 | 注释 |
|---|---|---|---|---|---|
| id | bigint | NO | NULL | auto_increment | 存储主键ID |
| connection_id | char(36) | NO | NULL |  | 连接业务ID |
| user_id | char(36) | NO | NULL |  | 资源归属用户ID |
| name | varchar(100) | NO | NULL |  | 显示名称 |
| provider | varchar(80) | NO | NULL |  | 提供方编码 |
| environment | varchar(80) | NO | NULL |  | 环境标识 |
| resource_policy_json | json | NO | NULL |  | 受控资源范围 |
| secret_ref | varchar(240) | NO | NULL |  | 密钥管理引用；不含明文 |
| create_idempotency_key | varchar(160) | YES | NULL |  | 创建幂等键；同键同用户复用原连接 |
| revision | bigint | NO | 0 |  | 配置授权版本 |
| enabled | tinyint | NO | 1 |  | 是否启用[0/1] |
| creator_id | varchar(36) | NO |  |  | 创建人ID |
| creator_name | varchar(50) | NO |  |  | 创建人姓名 |
| create_time | datetime(3) | NO | CURRENT_TIMESTAMP(3) | DEFAULT_GENERATED | 创建时间 |
| last_updater_id | varchar(36) | NO |  |  | 最后更新人ID |
| last_updater_name | varchar(50) | NO |  |  | 最后更新人姓名 |
| last_update_time | datetime(3) | NO | CURRENT_TIMESTAMP(3) | DEFAULT_GENERATED on update CURRENT_TIMESTAMP(3) | 最后更新时间 |
| del_flag | char(1) | NO | 0 |  | 删除标志[0-否,1-是] |

索引、外键与完整建表定义（SHOW CREATE TABLE；仅结构）：

```sql
CREATE TABLE `sq_connection` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '存储主键ID',
  `connection_id` char(36) COLLATE utf8mb4_bin NOT NULL COMMENT '连接业务ID',
  `user_id` char(36) COLLATE utf8mb4_bin NOT NULL COMMENT '资源归属用户ID',
  `name` varchar(100) COLLATE utf8mb4_bin NOT NULL COMMENT '显示名称',
  `provider` varchar(80) COLLATE utf8mb4_bin NOT NULL COMMENT '提供方编码',
  `environment` varchar(80) COLLATE utf8mb4_bin NOT NULL COMMENT '环境标识',
  `resource_policy_json` json NOT NULL COMMENT '受控资源范围',
  `secret_ref` varchar(240) COLLATE utf8mb4_bin NOT NULL COMMENT '密钥管理引用；不含明文',
  `create_idempotency_key` varchar(160) COLLATE utf8mb4_bin DEFAULT NULL COMMENT '创建幂等键；同键同用户复用原连接',
  `revision` bigint NOT NULL DEFAULT '0' COMMENT '配置授权版本',
  `enabled` tinyint NOT NULL DEFAULT '1' COMMENT '是否启用[0/1]',
  `creator_id` varchar(36) COLLATE utf8mb4_bin NOT NULL DEFAULT '' COMMENT '创建人ID',
  `creator_name` varchar(50) COLLATE utf8mb4_bin NOT NULL DEFAULT '' COMMENT '创建人姓名',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `last_updater_id` varchar(36) COLLATE utf8mb4_bin NOT NULL DEFAULT '' COMMENT '最后更新人ID',
  `last_updater_name` varchar(50) COLLATE utf8mb4_bin NOT NULL DEFAULT '' COMMENT '最后更新人姓名',
  `last_update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '最后更新时间',
  `del_flag` char(1) COLLATE utf8mb4_bin NOT NULL DEFAULT '0' COMMENT '删除标志[0-否,1-是]',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_owner_connection` (`user_id`,`connection_id`),
  UNIQUE KEY `uk_owner_create_idem` (`user_id`,`create_idempotency_key`),
  CONSTRAINT `ck_connection_del` CHECK ((`del_flag` in (_utf8mb4'0',_utf8mb4'1')))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='用户外部连接凭据引用';
```

## sq_context_snapshot

审计字段：完整七字段。

| 列 | 类型 | 可空 | 默认值 | 扩展 | 注释 |
|---|---|---|---|---|---|
| id | bigint | NO | NULL | auto_increment | 存储主键ID |
| snapshot_id | char(36) | NO | NULL |  | 局部上下文快照业务ID |
| user_id | char(36) | NO | NULL |  | 资源归属用户ID |
| task_id | char(36) | NO | NULL |  | 所属目标ID |
| target_node_key | varchar(80) | NO | NULL |  | 目标计划节点键 |
| logical_key | varchar(160) | NO | NULL |  | 快照幂等逻辑键 |
| conversation_generation | bigint | NO | NULL |  | 冻结时的对话代际 |
| memory_revision | bigint | NO | 0 |  | 记忆授权代际；D3 无长期记忆时为0 |
| items_json | json | NO | NULL |  | 经平台验证的局部材料及来源 |
| token_estimate | bigint | NO | NULL |  | 局部材料令牌估算 |
| content_hash | char(64) | NO | NULL |  | 局部材料规范化SHA256摘要 |
| creator_id | varchar(36) | NO |  |  | 创建人ID |
| creator_name | varchar(50) | NO |  |  | 创建人姓名 |
| create_time | datetime(3) | NO | CURRENT_TIMESTAMP(3) | DEFAULT_GENERATED | 创建时间 |
| last_updater_id | varchar(36) | NO |  |  | 最后更新人ID |
| last_updater_name | varchar(50) | NO |  |  | 最后更新人姓名 |
| last_update_time | datetime(3) | NO | CURRENT_TIMESTAMP(3) | DEFAULT_GENERATED on update CURRENT_TIMESTAMP(3) | 最后更新时间 |
| del_flag | char(1) | NO | 0 |  | 删除标志[0-否,1-是] |
| scope_id | char(36) | YES | NULL |  | 统一执行作用域ID |
| goal_revision | bigint | NO | 0 |  | 准备局部材料时的目标修订号 |

索引、外键与完整建表定义（SHOW CREATE TABLE；仅结构）：

```sql
CREATE TABLE `sq_context_snapshot` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '存储主键ID',
  `snapshot_id` char(36) COLLATE utf8mb4_bin NOT NULL COMMENT '局部上下文快照业务ID',
  `user_id` char(36) COLLATE utf8mb4_bin NOT NULL COMMENT '资源归属用户ID',
  `task_id` char(36) COLLATE utf8mb4_bin NOT NULL COMMENT '所属目标ID',
  `target_node_key` varchar(80) COLLATE utf8mb4_bin NOT NULL COMMENT '目标计划节点键',
  `logical_key` varchar(160) COLLATE utf8mb4_bin NOT NULL COMMENT '快照幂等逻辑键',
  `conversation_generation` bigint NOT NULL COMMENT '冻结时的对话代际',
  `memory_revision` bigint NOT NULL DEFAULT '0' COMMENT '记忆授权代际；D3 无长期记忆时为0',
  `items_json` json NOT NULL COMMENT '经平台验证的局部材料及来源',
  `token_estimate` bigint NOT NULL COMMENT '局部材料令牌估算',
  `content_hash` char(64) COLLATE utf8mb4_bin NOT NULL COMMENT '局部材料规范化SHA256摘要',
  `creator_id` varchar(36) COLLATE utf8mb4_bin NOT NULL DEFAULT '' COMMENT '创建人ID',
  `creator_name` varchar(50) COLLATE utf8mb4_bin NOT NULL DEFAULT '' COMMENT '创建人姓名',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `last_updater_id` varchar(36) COLLATE utf8mb4_bin NOT NULL DEFAULT '' COMMENT '最后更新人ID',
  `last_updater_name` varchar(50) COLLATE utf8mb4_bin NOT NULL DEFAULT '' COMMENT '最后更新人姓名',
  `last_update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '最后更新时间',
  `del_flag` char(1) COLLATE utf8mb4_bin NOT NULL DEFAULT '0' COMMENT '删除标志[0-否,1-是]',
  `scope_id` char(36) COLLATE utf8mb4_bin DEFAULT NULL COMMENT '统一执行作用域ID',
  `goal_revision` bigint NOT NULL DEFAULT '0' COMMENT '准备局部材料时的目标修订号',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_context_snapshot` (`snapshot_id`),
  UNIQUE KEY `uk_context_logical` (`task_id`,`target_node_key`,`logical_key`),
  KEY `idx_context_owner_task` (`user_id`,`task_id`),
  KEY `fk_context_snapshot_scope` (`user_id`,`scope_id`),
  CONSTRAINT `fk_context_snapshot_scope` FOREIGN KEY (`user_id`, `scope_id`) REFERENCES `sq_execution_scope` (`user_id`, `scope_id`),
  CONSTRAINT `fk_context_task` FOREIGN KEY (`user_id`, `task_id`) REFERENCES `sq_goal_task` (`user_id`, `task_id`),
  CONSTRAINT `ck_context_del` CHECK ((`del_flag` in (_utf8mb4'0',_utf8mb4'1')))
) ENGINE=InnoDB AUTO_INCREMENT=3 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='Worker授权局部上下文快照';
```

## sq_conversation

审计字段：完整七字段。

| 列 | 类型 | 可空 | 默认值 | 扩展 | 注释 |
|---|---|---|---|---|---|
| id | bigint | NO | NULL | auto_increment | 存储主键ID |
| conversation_id | char(36) | NO | NULL |  | 对话业务ID |
| user_id | char(36) | NO | NULL |  | 资源归属用户ID |
| title | varchar(200) | NO | NULL |  | 用户对话标题 |
| generation | bigint | NO | 0 |  | 会话代数 |
| status | varchar(16) | NO | NULL |  | ACTIVE/DELETING/DELETED |
| active_task_id | char(36) | YES | NULL |  | 未终结目标ID |
| version | bigint | NO | 0 |  | 业务版本 |
| creator_id | varchar(36) | NO |  |  | 创建人ID |
| creator_name | varchar(50) | NO |  |  | 创建人姓名 |
| create_time | datetime(3) | NO | CURRENT_TIMESTAMP(3) | DEFAULT_GENERATED | 创建时间 |
| last_updater_id | varchar(36) | NO |  |  | 最后更新人ID |
| last_updater_name | varchar(50) | NO |  |  | 最后更新人姓名 |
| last_update_time | datetime(3) | NO | CURRENT_TIMESTAMP(3) | DEFAULT_GENERATED on update CURRENT_TIMESTAMP(3) | 最后更新时间 |
| del_flag | char(1) | NO | 0 |  | 删除标志[0-否,1-是] |
| message_seq | bigint | NO | 0 |  | 已分配的会话消息序号 |
| conversation_event_seq | bigint | NO | 0 |  | 会话公开事件水位 |
| title_source | varchar(20) | NO | LEGACY |  | 标题来源[LEGACY,FALLBACK,AUTO_PENDING,AUTO,MANUAL] |
| title_version | bigint | NO | 0 |  | 标题独立乐观版本 |
| last_activity_at | datetime(3) | YES | NULL |  | 最后用户交流或助手交付时间 |

索引、外键与完整建表定义（SHOW CREATE TABLE；仅结构）：

```sql
CREATE TABLE `sq_conversation` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '存储主键ID',
  `conversation_id` char(36) COLLATE utf8mb4_bin NOT NULL COMMENT '对话业务ID',
  `user_id` char(36) COLLATE utf8mb4_bin NOT NULL COMMENT '资源归属用户ID',
  `title` varchar(200) COLLATE utf8mb4_bin NOT NULL COMMENT '用户对话标题',
  `generation` bigint NOT NULL DEFAULT '0' COMMENT '会话代数',
  `status` varchar(16) COLLATE utf8mb4_bin NOT NULL COMMENT 'ACTIVE/DELETING/DELETED',
  `active_task_id` char(36) COLLATE utf8mb4_bin DEFAULT NULL COMMENT '未终结目标ID',
  `version` bigint NOT NULL DEFAULT '0' COMMENT '业务版本',
  `creator_id` varchar(36) COLLATE utf8mb4_bin NOT NULL DEFAULT '' COMMENT '创建人ID',
  `creator_name` varchar(50) COLLATE utf8mb4_bin NOT NULL DEFAULT '' COMMENT '创建人姓名',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `last_updater_id` varchar(36) COLLATE utf8mb4_bin NOT NULL DEFAULT '' COMMENT '最后更新人ID',
  `last_updater_name` varchar(50) COLLATE utf8mb4_bin NOT NULL DEFAULT '' COMMENT '最后更新人姓名',
  `last_update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '最后更新时间',
  `del_flag` char(1) COLLATE utf8mb4_bin NOT NULL DEFAULT '0' COMMENT '删除标志[0-否,1-是]',
  `message_seq` bigint NOT NULL DEFAULT '0' COMMENT '已分配的会话消息序号',
  `conversation_event_seq` bigint NOT NULL DEFAULT '0' COMMENT '会话公开事件水位',
  `title_source` varchar(20) COLLATE utf8mb4_bin NOT NULL DEFAULT 'LEGACY' COMMENT '标题来源[LEGACY,FALLBACK,AUTO_PENDING,AUTO,MANUAL]',
  `title_version` bigint NOT NULL DEFAULT '0' COMMENT '标题独立乐观版本',
  `last_activity_at` datetime(3) DEFAULT NULL COMMENT '最后用户交流或助手交付时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_owner_conversation` (`user_id`,`conversation_id`),
  CONSTRAINT `ck_conversation_del` CHECK ((`del_flag` in (_utf8mb4'0',_utf8mb4'1')))
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='用户对话';
```

## sq_conversation_event

审计字段：完整七字段。

| 列 | 类型 | 可空 | 默认值 | 扩展 | 注释 |
|---|---|---|---|---|---|
| id | bigint | NO | NULL | auto_increment | 存储主键ID |
| user_id | char(36) | NO | NULL |  | 资源归属用户ID |
| conversation_id | char(36) | NO | NULL |  | 所属会话ID |
| generation | bigint | NO | NULL |  | 事件所属会话代际 |
| event_seq | bigint | NO | NULL |  | 会话内递增公开事件序号 |
| producer_key | varchar(200) | NO | NULL |  | 稳定生产事件键 |
| event_type | varchar(64) | NO | NULL |  | 公开事件类型 |
| payload_json | json | NO | NULL |  | 脱敏公开事件载荷 |
| occurred_at | datetime(3) | NO | NULL |  | 发生时间 |
| creator_id | varchar(36) | NO |  |  | 创建人ID |
| creator_name | varchar(50) | NO |  |  | 创建人姓名 |
| create_time | datetime(3) | NO | CURRENT_TIMESTAMP(3) | DEFAULT_GENERATED | 创建时间 |
| last_updater_id | varchar(36) | NO |  |  | 最后更新人ID |
| last_updater_name | varchar(50) | NO |  |  | 最后更新人姓名 |
| last_update_time | datetime(3) | NO | CURRENT_TIMESTAMP(3) | DEFAULT_GENERATED on update CURRENT_TIMESTAMP(3) | 最后更新时间 |
| del_flag | char(1) | NO | 0 |  | 删除标志[0-否,1-是] |

索引、外键与完整建表定义（SHOW CREATE TABLE；仅结构）：

```sql
CREATE TABLE `sq_conversation_event` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '存储主键ID',
  `user_id` char(36) COLLATE utf8mb4_bin NOT NULL COMMENT '资源归属用户ID',
  `conversation_id` char(36) COLLATE utf8mb4_bin NOT NULL COMMENT '所属会话ID',
  `generation` bigint NOT NULL COMMENT '事件所属会话代际',
  `event_seq` bigint NOT NULL COMMENT '会话内递增公开事件序号',
  `producer_key` varchar(200) COLLATE utf8mb4_bin NOT NULL COMMENT '稳定生产事件键',
  `event_type` varchar(64) COLLATE utf8mb4_bin NOT NULL COMMENT '公开事件类型',
  `payload_json` json NOT NULL COMMENT '脱敏公开事件载荷',
  `occurred_at` datetime(3) NOT NULL COMMENT '发生时间',
  `creator_id` varchar(36) COLLATE utf8mb4_bin NOT NULL DEFAULT '' COMMENT '创建人ID',
  `creator_name` varchar(50) COLLATE utf8mb4_bin NOT NULL DEFAULT '' COMMENT '创建人姓名',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `last_updater_id` varchar(36) COLLATE utf8mb4_bin NOT NULL DEFAULT '' COMMENT '最后更新人ID',
  `last_updater_name` varchar(50) COLLATE utf8mb4_bin NOT NULL DEFAULT '' COMMENT '最后更新人姓名',
  `last_update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '最后更新时间',
  `del_flag` char(1) COLLATE utf8mb4_bin NOT NULL DEFAULT '0' COMMENT '删除标志[0-否,1-是]',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_conversation_event_seq` (`user_id`,`conversation_id`,`event_seq`),
  UNIQUE KEY `uk_conversation_event_producer` (`user_id`,`conversation_id`,`producer_key`),
  CONSTRAINT `fk_conversation_event_owner` FOREIGN KEY (`user_id`, `conversation_id`) REFERENCES `sq_conversation` (`user_id`, `conversation_id`),
  CONSTRAINT `ck_conversation_event_del` CHECK ((`del_flag` in (_utf8mb4'0',_utf8mb4'1')))
) ENGINE=InnoDB AUTO_INCREMENT=18 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='会话一致前缀公开事件';
```

## sq_execution_failure

审计字段：完整七字段。

| 列 | 类型 | 可空 | 默认值 | 扩展 | 注释 |
|---|---|---|---|---|---|
| id | bigint | NO | NULL | auto_increment | 存储主键ID |
| failure_id | char(36) | NO | NULL |  | 故障业务ID |
| user_id | char(36) | NO | NULL |  | 归属用户ID |
| scope_id | char(36) | NO | NULL |  | 受权执行作用域ID |
| binding_id | char(36) | NO | NULL |  | 故障来源原生绑定ID |
| failure_key | char(64) | NO | NULL |  | 稳定故障来源摘要 |
| category | varchar(32) | NO | NULL |  | 故障分类 |
| code | varchar(64) | NO | NULL |  | 稳定原因码 |
| actor | varchar(16) | NO | NULL |  | 处理责任[SYSTEM,USER,OPERATOR] |
| safe_message | varchar(500) | NO | NULL |  | 面向用户的安全说明 |
| diagnostic_id | char(36) | NO | NULL |  | 内部诊断关联ID，不含原始异常 |
| status | varchar(16) | NO | ACTIVE |  | 状态[ACTIVE,RETRYING,RESOLVED] |
| retry_attempt | int | NO | 0 |  | 该故障已批准的固定重试序号，避免并行执行改变计费身份 |
| creator_id | varchar(36) | NO |  |  | 创建人ID |
| creator_name | varchar(50) | NO |  |  | 创建人姓名 |
| create_time | datetime(3) | NO | CURRENT_TIMESTAMP(3) | DEFAULT_GENERATED | 创建时间 |
| last_updater_id | varchar(36) | NO |  |  | 最后更新人ID |
| last_updater_name | varchar(50) | NO |  |  | 最后更新人姓名 |
| last_update_time | datetime(3) | NO | CURRENT_TIMESTAMP(3) | DEFAULT_GENERATED on update CURRENT_TIMESTAMP(3) | 最后更新时间 |
| del_flag | char(1) | NO | 0 |  | 删除标志[0-否,1-是] |

索引、外键与完整建表定义（SHOW CREATE TABLE；仅结构）：

```sql
CREATE TABLE `sq_execution_failure` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '存储主键ID',
  `failure_id` char(36) COLLATE utf8mb4_bin NOT NULL COMMENT '故障业务ID',
  `user_id` char(36) COLLATE utf8mb4_bin NOT NULL COMMENT '归属用户ID',
  `scope_id` char(36) COLLATE utf8mb4_bin NOT NULL COMMENT '受权执行作用域ID',
  `binding_id` char(36) COLLATE utf8mb4_bin NOT NULL COMMENT '故障来源原生绑定ID',
  `failure_key` char(64) COLLATE utf8mb4_bin NOT NULL COMMENT '稳定故障来源摘要',
  `category` varchar(32) COLLATE utf8mb4_bin NOT NULL COMMENT '故障分类',
  `code` varchar(64) COLLATE utf8mb4_bin NOT NULL COMMENT '稳定原因码',
  `actor` varchar(16) COLLATE utf8mb4_bin NOT NULL COMMENT '处理责任[SYSTEM,USER,OPERATOR]',
  `safe_message` varchar(500) COLLATE utf8mb4_bin NOT NULL COMMENT '面向用户的安全说明',
  `diagnostic_id` char(36) COLLATE utf8mb4_bin NOT NULL COMMENT '内部诊断关联ID，不含原始异常',
  `status` varchar(16) COLLATE utf8mb4_bin NOT NULL DEFAULT 'ACTIVE' COMMENT '状态[ACTIVE,RETRYING,RESOLVED]',
  `retry_attempt` int NOT NULL DEFAULT '0' COMMENT '该故障已批准的固定重试序号，避免并行执行改变计费身份',
  `creator_id` varchar(36) COLLATE utf8mb4_bin NOT NULL DEFAULT '' COMMENT '创建人ID',
  `creator_name` varchar(50) COLLATE utf8mb4_bin NOT NULL DEFAULT '' COMMENT '创建人姓名',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `last_updater_id` varchar(36) COLLATE utf8mb4_bin NOT NULL DEFAULT '' COMMENT '最后更新人ID',
  `last_updater_name` varchar(50) COLLATE utf8mb4_bin NOT NULL DEFAULT '' COMMENT '最后更新人姓名',
  `last_update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '最后更新时间',
  `del_flag` char(1) COLLATE utf8mb4_bin NOT NULL DEFAULT '0' COMMENT '删除标志[0-否,1-是]',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_execution_failure` (`user_id`,`failure_id`),
  UNIQUE KEY `uk_execution_failure_source` (`user_id`,`scope_id`,`failure_key`),
  KEY `idx_failure_scope` (`user_id`,`scope_id`,`id`),
  CONSTRAINT `fk_failure_scope` FOREIGN KEY (`user_id`, `scope_id`) REFERENCES `sq_execution_scope` (`user_id`, `scope_id`),
  CONSTRAINT `ck_failure_del` CHECK ((`del_flag` in (_utf8mb4'0',_utf8mb4'1'))),
  CONSTRAINT `ck_failure_status` CHECK ((`status` in (_utf8mb4'ACTIVE',_utf8mb4'RETRYING',_utf8mb4'RESOLVED')))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='执行的结构化故障事实';
```

## sq_execution_scope

审计字段：完整七字段。

| 列 | 类型 | 可空 | 默认值 | 扩展 | 注释 |
|---|---|---|---|---|---|
| id | bigint | NO | NULL | auto_increment | 存储主键ID |
| scope_id | char(36) | NO | NULL |  | 执行作用域业务ID |
| user_id | char(36) | NO | NULL |  | 资源归属用户ID |
| conversation_id | char(36) | NO | NULL |  | 所属会话ID |
| conversation_generation | bigint | NO | NULL |  | 受理时会话代际 |
| kind | varchar(16) | NO | NULL |  | 归属类型[RESPONSE,TASK] |
| owner_id | char(36) | NO | NULL |  | 回复执行或目标业务ID |
| account_id | char(36) | NO | NULL |  | 共享预算账户ID |
| creator_id | varchar(36) | NO |  |  | 创建人ID |
| creator_name | varchar(50) | NO |  |  | 创建人姓名 |
| create_time | datetime(3) | NO | CURRENT_TIMESTAMP(3) | DEFAULT_GENERATED | 创建时间 |
| last_updater_id | varchar(36) | NO |  |  | 最后更新人ID |
| last_updater_name | varchar(50) | NO |  |  | 最后更新人姓名 |
| last_update_time | datetime(3) | NO | CURRENT_TIMESTAMP(3) | DEFAULT_GENERATED on update CURRENT_TIMESTAMP(3) | 最后更新时间 |
| del_flag | char(1) | NO | 0 |  | 删除标志[0-否,1-是] |
| service_retry_attempts | int | NO | 0 |  | 已持久自动服务重试次数，不因重启清零 |
| plan_correction_attempts | int | NO | 0 |  | 无效计划次数，首次失败后最多两次纠正 |
| retry_next_attempt_at | datetime(3) | YES | NULL |  | 已排队恢复的执行时间 |

索引、外键与完整建表定义（SHOW CREATE TABLE；仅结构）：

```sql
CREATE TABLE `sq_execution_scope` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '存储主键ID',
  `scope_id` char(36) COLLATE utf8mb4_bin NOT NULL COMMENT '执行作用域业务ID',
  `user_id` char(36) COLLATE utf8mb4_bin NOT NULL COMMENT '资源归属用户ID',
  `conversation_id` char(36) COLLATE utf8mb4_bin NOT NULL COMMENT '所属会话ID',
  `conversation_generation` bigint NOT NULL COMMENT '受理时会话代际',
  `kind` varchar(16) COLLATE utf8mb4_bin NOT NULL COMMENT '归属类型[RESPONSE,TASK]',
  `owner_id` char(36) COLLATE utf8mb4_bin NOT NULL COMMENT '回复执行或目标业务ID',
  `account_id` char(36) COLLATE utf8mb4_bin NOT NULL COMMENT '共享预算账户ID',
  `creator_id` varchar(36) COLLATE utf8mb4_bin NOT NULL DEFAULT '' COMMENT '创建人ID',
  `creator_name` varchar(50) COLLATE utf8mb4_bin NOT NULL DEFAULT '' COMMENT '创建人姓名',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `last_updater_id` varchar(36) COLLATE utf8mb4_bin NOT NULL DEFAULT '' COMMENT '最后更新人ID',
  `last_updater_name` varchar(50) COLLATE utf8mb4_bin NOT NULL DEFAULT '' COMMENT '最后更新人姓名',
  `last_update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '最后更新时间',
  `del_flag` char(1) COLLATE utf8mb4_bin NOT NULL DEFAULT '0' COMMENT '删除标志[0-否,1-是]',
  `service_retry_attempts` int NOT NULL DEFAULT '0' COMMENT '已持久自动服务重试次数，不因重启清零',
  `plan_correction_attempts` int NOT NULL DEFAULT '0' COMMENT '无效计划次数，首次失败后最多两次纠正',
  `retry_next_attempt_at` datetime(3) DEFAULT NULL COMMENT '已排队恢复的执行时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_execution_scope` (`user_id`,`scope_id`),
  UNIQUE KEY `uk_scope_owner` (`user_id`,`kind`,`owner_id`),
  KEY `fk_scope_conversation` (`user_id`,`conversation_id`),
  KEY `fk_scope_account` (`user_id`,`account_id`),
  CONSTRAINT `fk_scope_account` FOREIGN KEY (`user_id`, `account_id`) REFERENCES `sq_budget_account` (`user_id`, `account_id`),
  CONSTRAINT `fk_scope_conversation` FOREIGN KEY (`user_id`, `conversation_id`) REFERENCES `sq_conversation` (`user_id`, `conversation_id`),
  CONSTRAINT `ck_execution_scope_del` CHECK ((`del_flag` in (_utf8mb4'0',_utf8mb4'1'))),
  CONSTRAINT `ck_scope_kind` CHECK ((`kind` in (_utf8mb4'RESPONSE',_utf8mb4'TASK')))
) ENGINE=InnoDB AUTO_INCREMENT=3 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='统一执行归属与授权边界';
```

## sq_goal_task

审计字段：完整七字段。

| 列 | 类型 | 可空 | 默认值 | 扩展 | 注释 |
|---|---|---|---|---|---|
| id | bigint | NO | NULL | auto_increment | 存储主键ID |
| task_id | char(36) | NO | NULL |  | 目标业务ID |
| user_id | char(36) | NO | NULL |  | 资源归属用户ID |
| conversation_id | char(36) | NO | NULL |  | 所属对话ID |
| status | varchar(24) | NO | NULL |  | 产品状态 |
| version | bigint | NO | 0 |  | 业务版本 |
| event_seq | bigint | NO | 0 |  | 公开事件水位 |
| input_json | json | NO | NULL |  | 规范化用户输入 |
| plan_version | bigint | NO | 0 |  | 计划版本 |
| answer_revision | bigint | NO | 0 |  | 用户可见最终答案版本 |
| cancel_requested_at | datetime(3) | YES | NULL |  | 取消意图时间，完成裁决屏障 |
| completion_proposal_json | json | YES | NULL |  | Master 提交的完成候选，非最终结果 |
| proposal_binding_id | char(36) | YES | NULL |  | 候选来源绑定ID |
| proposal_turn_id | varchar(64) | YES | NULL |  | 候选来源原生TurnID |
| proposal_tool_call_id | varchar(128) | YES | NULL |  | 候选来源工具调用ID |
| proposal_hash | char(64) | YES | NULL |  | 候选内容摘要，用于重放校验 |
| completion_correction_count | int | NO | 0 |  | 完成候选缺失后的系统纠正次数 |
| blocked_reason | varchar(1000) | YES | NULL |  | 当前恢复阻塞原因；不保存原生异常正文 |
| creator_id | varchar(36) | NO |  |  | 创建人ID |
| creator_name | varchar(50) | NO |  |  | 创建人姓名 |
| create_time | datetime(3) | NO | CURRENT_TIMESTAMP(3) | DEFAULT_GENERATED | 创建时间 |
| last_updater_id | varchar(36) | NO |  |  | 最后更新人ID |
| last_updater_name | varchar(50) | NO |  |  | 最后更新人姓名 |
| last_update_time | datetime(3) | NO | CURRENT_TIMESTAMP(3) | DEFAULT_GENERATED on update CURRENT_TIMESTAMP(3) | 最后更新时间 |
| del_flag | char(1) | NO | 0 |  | 删除标志[0-否,1-是] |
| budget_profile_key | varchar(80) | NO | STANDARD |  | 目标受理时固定的预算Profile键 |
| budget_limit_microunits | bigint | NO | 5000000 |  | 父目标共享预算上限，整数微单位 |
| scope_id | char(36) | YES | NULL |  | 目标执行作用域ID |
| origin_message_id | char(36) | YES | NULL |  | Master创建目标的来源用户消息ID |
| origin_execution_id | char(36) | YES | NULL |  | Master创建目标的来源回复执行ID |
| goal_revision | bigint | NO | 0 |  | 已应用目标语义修订号 |

索引、外键与完整建表定义（SHOW CREATE TABLE；仅结构）：

```sql
CREATE TABLE `sq_goal_task` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '存储主键ID',
  `task_id` char(36) COLLATE utf8mb4_bin NOT NULL COMMENT '目标业务ID',
  `user_id` char(36) COLLATE utf8mb4_bin NOT NULL COMMENT '资源归属用户ID',
  `conversation_id` char(36) COLLATE utf8mb4_bin NOT NULL COMMENT '所属对话ID',
  `status` varchar(24) COLLATE utf8mb4_bin NOT NULL COMMENT '产品状态',
  `version` bigint NOT NULL DEFAULT '0' COMMENT '业务版本',
  `event_seq` bigint NOT NULL DEFAULT '0' COMMENT '公开事件水位',
  `input_json` json NOT NULL COMMENT '规范化用户输入',
  `plan_version` bigint NOT NULL DEFAULT '0' COMMENT '计划版本',
  `answer_revision` bigint NOT NULL DEFAULT '0' COMMENT '用户可见最终答案版本',
  `cancel_requested_at` datetime(3) DEFAULT NULL COMMENT '取消意图时间，完成裁决屏障',
  `completion_proposal_json` json DEFAULT NULL COMMENT 'Master 提交的完成候选，非最终结果',
  `proposal_binding_id` char(36) COLLATE utf8mb4_bin DEFAULT NULL COMMENT '候选来源绑定ID',
  `proposal_turn_id` varchar(64) COLLATE utf8mb4_bin DEFAULT NULL COMMENT '候选来源原生TurnID',
  `proposal_tool_call_id` varchar(128) COLLATE utf8mb4_bin DEFAULT NULL COMMENT '候选来源工具调用ID',
  `proposal_hash` char(64) COLLATE utf8mb4_bin DEFAULT NULL COMMENT '候选内容摘要，用于重放校验',
  `completion_correction_count` int NOT NULL DEFAULT '0' COMMENT '完成候选缺失后的系统纠正次数',
  `blocked_reason` varchar(1000) COLLATE utf8mb4_bin DEFAULT NULL COMMENT '当前恢复阻塞原因；不保存原生异常正文',
  `creator_id` varchar(36) COLLATE utf8mb4_bin NOT NULL DEFAULT '' COMMENT '创建人ID',
  `creator_name` varchar(50) COLLATE utf8mb4_bin NOT NULL DEFAULT '' COMMENT '创建人姓名',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `last_updater_id` varchar(36) COLLATE utf8mb4_bin NOT NULL DEFAULT '' COMMENT '最后更新人ID',
  `last_updater_name` varchar(50) COLLATE utf8mb4_bin NOT NULL DEFAULT '' COMMENT '最后更新人姓名',
  `last_update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '最后更新时间',
  `del_flag` char(1) COLLATE utf8mb4_bin NOT NULL DEFAULT '0' COMMENT '删除标志[0-否,1-是]',
  `budget_profile_key` varchar(80) COLLATE utf8mb4_bin NOT NULL DEFAULT 'STANDARD' COMMENT '目标受理时固定的预算Profile键',
  `budget_limit_microunits` bigint NOT NULL DEFAULT '5000000' COMMENT '父目标共享预算上限，整数微单位',
  `scope_id` char(36) COLLATE utf8mb4_bin DEFAULT NULL COMMENT '目标执行作用域ID',
  `origin_message_id` char(36) COLLATE utf8mb4_bin DEFAULT NULL COMMENT 'Master创建目标的来源用户消息ID',
  `origin_execution_id` char(36) COLLATE utf8mb4_bin DEFAULT NULL COMMENT 'Master创建目标的来源回复执行ID',
  `goal_revision` bigint NOT NULL DEFAULT '0' COMMENT '已应用目标语义修订号',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_owner_task` (`user_id`,`task_id`),
  KEY `fk_task_conversation` (`user_id`,`conversation_id`),
  KEY `fk_task_scope` (`user_id`,`scope_id`),
  KEY `fk_task_origin_message` (`user_id`,`origin_message_id`),
  CONSTRAINT `fk_task_conversation` FOREIGN KEY (`user_id`, `conversation_id`) REFERENCES `sq_conversation` (`user_id`, `conversation_id`),
  CONSTRAINT `fk_task_origin_message` FOREIGN KEY (`user_id`, `origin_message_id`) REFERENCES `sq_message` (`user_id`, `message_id`),
  CONSTRAINT `fk_task_scope` FOREIGN KEY (`user_id`, `scope_id`) REFERENCES `sq_execution_scope` (`user_id`, `scope_id`),
  CONSTRAINT `ck_goal_task_del` CHECK ((`del_flag` in (_utf8mb4'0',_utf8mb4'1')))
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='用户目标';
```

## sq_interaction

审计字段：完整七字段。

| 列 | 类型 | 可空 | 默认值 | 扩展 | 注释 |
|---|---|---|---|---|---|
| id | bigint | NO | NULL | auto_increment | 存储主键ID |
| interaction_id | char(36) | NO | NULL |  | 交互ID |
| user_id | char(36) | NO | NULL |  | 资源归属用户ID |
| task_id | char(36) | YES | NULL |  | 可选所属目标ID；普通回复为空 |
| job_id | char(36) | YES | NULL |  | 节点ID |
| binding_id | char(36) | YES | NULL |  | 所属绑定 |
| operation_id | char(36) | YES | NULL |  | 关联操作 |
| origin | varchar(16) | NO | NULL |  | BUSINESS/FRAMEWORK |
| framework_request_id | varchar(200) | YES | NULL |  | 真实原生请求ID；业务交互为空 |
| kind | varchar(16) | NO | NULL |  | INPUT/APPROVAL |
| question | varchar(4000) | NO | NULL |  | 用户问题 |
| answer_schema_json | json | NO | NULL |  | 回复JSON Schema |
| plan_version | bigint | NO | 0 |  | 绑定计划版本 |
| request_hash | varchar(64) | NO | NULL |  | 绑定请求摘要 |
| auth_version | bigint | NO | 0 |  | 确认授权版本 |
| connection_revision | bigint | NO | 0 |  | 确认连接版本 |
| expires_at | datetime(3) | NO | NULL |  | 答复有效期 |
| status | varchar(16) | NO | NULL |  | OPEN/RESPONDED/EXPIRED/INVALIDATED/CANCELLED |
| version | bigint | NO | 0 |  | 乐观版本 |
| response_json | json | YES | NULL |  | 受Schema校验的用户回复 |
| creator_id | varchar(36) | NO |  |  | 创建人ID |
| creator_name | varchar(50) | NO |  |  | 创建人姓名 |
| create_time | datetime(3) | NO | CURRENT_TIMESTAMP(3) | DEFAULT_GENERATED | 创建时间 |
| last_updater_id | varchar(36) | NO |  |  | 最后更新人ID |
| last_updater_name | varchar(50) | NO |  |  | 最后更新人姓名 |
| last_update_time | datetime(3) | NO | CURRENT_TIMESTAMP(3) | DEFAULT_GENERATED on update CURRENT_TIMESTAMP(3) | 最后更新时间 |
| del_flag | char(1) | NO | 0 |  | 删除标志[0-否,1-是] |
| scope_id | char(36) | YES | NULL |  | 统一执行作用域ID |

索引、外键与完整建表定义（SHOW CREATE TABLE；仅结构）：

```sql
CREATE TABLE `sq_interaction` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '存储主键ID',
  `interaction_id` char(36) COLLATE utf8mb4_bin NOT NULL COMMENT '交互ID',
  `user_id` char(36) COLLATE utf8mb4_bin NOT NULL COMMENT '资源归属用户ID',
  `task_id` char(36) COLLATE utf8mb4_bin DEFAULT NULL COMMENT '可选所属目标ID；普通回复为空',
  `job_id` char(36) COLLATE utf8mb4_bin DEFAULT NULL COMMENT '节点ID',
  `binding_id` char(36) COLLATE utf8mb4_bin DEFAULT NULL COMMENT '所属绑定',
  `operation_id` char(36) COLLATE utf8mb4_bin DEFAULT NULL COMMENT '关联操作',
  `origin` varchar(16) COLLATE utf8mb4_bin NOT NULL COMMENT 'BUSINESS/FRAMEWORK',
  `framework_request_id` varchar(200) COLLATE utf8mb4_bin DEFAULT NULL COMMENT '真实原生请求ID；业务交互为空',
  `kind` varchar(16) COLLATE utf8mb4_bin NOT NULL COMMENT 'INPUT/APPROVAL',
  `question` varchar(4000) COLLATE utf8mb4_bin NOT NULL COMMENT '用户问题',
  `answer_schema_json` json NOT NULL COMMENT '回复JSON Schema',
  `plan_version` bigint NOT NULL DEFAULT '0' COMMENT '绑定计划版本',
  `request_hash` varchar(64) COLLATE utf8mb4_bin NOT NULL COMMENT '绑定请求摘要',
  `auth_version` bigint NOT NULL DEFAULT '0' COMMENT '确认授权版本',
  `connection_revision` bigint NOT NULL DEFAULT '0' COMMENT '确认连接版本',
  `expires_at` datetime(3) NOT NULL COMMENT '答复有效期',
  `status` varchar(16) COLLATE utf8mb4_bin NOT NULL COMMENT 'OPEN/RESPONDED/EXPIRED/INVALIDATED/CANCELLED',
  `version` bigint NOT NULL DEFAULT '0' COMMENT '乐观版本',
  `response_json` json DEFAULT NULL COMMENT '受Schema校验的用户回复',
  `creator_id` varchar(36) COLLATE utf8mb4_bin NOT NULL DEFAULT '' COMMENT '创建人ID',
  `creator_name` varchar(50) COLLATE utf8mb4_bin NOT NULL DEFAULT '' COMMENT '创建人姓名',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `last_updater_id` varchar(36) COLLATE utf8mb4_bin NOT NULL DEFAULT '' COMMENT '最后更新人ID',
  `last_updater_name` varchar(50) COLLATE utf8mb4_bin NOT NULL DEFAULT '' COMMENT '最后更新人姓名',
  `last_update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '最后更新时间',
  `del_flag` char(1) COLLATE utf8mb4_bin NOT NULL DEFAULT '0' COMMENT '删除标志[0-否,1-是]',
  `scope_id` char(36) COLLATE utf8mb4_bin DEFAULT NULL COMMENT '统一执行作用域ID',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_owner_interaction` (`user_id`,`interaction_id`),
  UNIQUE KEY `uk_framework_request` (`user_id`,`binding_id`,`framework_request_id`),
  KEY `fk_interaction_scope` (`user_id`,`scope_id`),
  CONSTRAINT `fk_interaction_scope` FOREIGN KEY (`user_id`, `scope_id`) REFERENCES `sq_execution_scope` (`user_id`, `scope_id`),
  CONSTRAINT `ck_interaction_del` CHECK ((`del_flag` in (_utf8mb4'0',_utf8mb4'1')))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='持久输入与审批请求';
```

## sq_memory

审计字段：完整七字段。

| 列 | 类型 | 可空 | 默认值 | 扩展 | 注释 |
|---|---|---|---|---|---|
| id | bigint | NO | NULL | auto_increment | 存储主键ID |
| memory_id | char(36) | NO | NULL |  | 记忆业务ID |
| user_id | char(36) | NO | NULL |  | 归属用户ID |
| memory_key | varchar(100) | NO | NULL |  | 用户记忆键；删除后可由新记忆重复使用 |
| content | text | NO | NULL |  | 显式记忆正文，删除后清空 |
| status | varchar(16) | NO | ACTIVE |  | 记忆状态[ACTIVE,DELETED] |
| version | bigint | NO | 0 |  | 记忆内容乐观版本 |
| expires_at | datetime(3) | YES | NULL |  | 可选失效时间 |
| source_message_id | char(36) | YES | NULL |  | 工具写入的受权用户消息ID，设置页写入为空 |
| source_quote | text | YES | NULL |  | 用户显式授权原话片段，删除后清空 |
| creator_id | varchar(36) | NO |  |  | 创建人ID |
| creator_name | varchar(50) | NO |  |  | 创建人姓名 |
| create_time | datetime(3) | NO | CURRENT_TIMESTAMP(3) | DEFAULT_GENERATED | 创建时间 |
| last_updater_id | varchar(36) | NO |  |  | 最后更新人ID |
| last_updater_name | varchar(50) | NO |  |  | 最后更新人姓名 |
| last_update_time | datetime(3) | NO | CURRENT_TIMESTAMP(3) | DEFAULT_GENERATED on update CURRENT_TIMESTAMP(3) | 最后更新时间 |
| del_flag | char(1) | NO | 0 |  | 删除标志[0-否,1-是] |
| active_key | varchar(100) | YES | NULL | STORED GENERATED | 有效记忆键；已删除记录为空以允许显式重新创建 |

索引、外键与完整建表定义（SHOW CREATE TABLE；仅结构）：

```sql
CREATE TABLE `sq_memory` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '存储主键ID',
  `memory_id` char(36) COLLATE utf8mb4_bin NOT NULL COMMENT '记忆业务ID',
  `user_id` char(36) COLLATE utf8mb4_bin NOT NULL COMMENT '归属用户ID',
  `memory_key` varchar(100) COLLATE utf8mb4_bin NOT NULL COMMENT '用户记忆键；删除后可由新记忆重复使用',
  `content` text COLLATE utf8mb4_bin NOT NULL COMMENT '显式记忆正文，删除后清空',
  `status` varchar(16) COLLATE utf8mb4_bin NOT NULL DEFAULT 'ACTIVE' COMMENT '记忆状态[ACTIVE,DELETED]',
  `version` bigint NOT NULL DEFAULT '0' COMMENT '记忆内容乐观版本',
  `expires_at` datetime(3) DEFAULT NULL COMMENT '可选失效时间',
  `source_message_id` char(36) COLLATE utf8mb4_bin DEFAULT NULL COMMENT '工具写入的受权用户消息ID，设置页写入为空',
  `source_quote` text COLLATE utf8mb4_bin COMMENT '用户显式授权原话片段，删除后清空',
  `creator_id` varchar(36) COLLATE utf8mb4_bin NOT NULL DEFAULT '' COMMENT '创建人ID',
  `creator_name` varchar(50) COLLATE utf8mb4_bin NOT NULL DEFAULT '' COMMENT '创建人姓名',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `last_updater_id` varchar(36) COLLATE utf8mb4_bin NOT NULL DEFAULT '' COMMENT '最后更新人ID',
  `last_updater_name` varchar(50) COLLATE utf8mb4_bin NOT NULL DEFAULT '' COMMENT '最后更新人姓名',
  `last_update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '最后更新时间',
  `del_flag` char(1) COLLATE utf8mb4_bin NOT NULL DEFAULT '0' COMMENT '删除标志[0-否,1-是]',
  `active_key` varchar(100) COLLATE utf8mb4_bin GENERATED ALWAYS AS ((case when (`status` = _utf8mb4'ACTIVE') then `memory_key` else NULL end)) STORED COMMENT '有效记忆键；已删除记录为空以允许显式重新创建',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_memory_identity` (`user_id`,`memory_id`),
  UNIQUE KEY `uk_memory_active_key` (`user_id`,`active_key`),
  KEY `idx_memory_active` (`user_id`,`status`,`id`),
  CONSTRAINT `ck_memory_status` CHECK ((`status` in (_utf8mb4'ACTIVE',_utf8mb4'DELETED')))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='用户显式长期记忆及删除墓碑';
```

## sq_memory_revision

审计字段：完整七字段。

| 列 | 类型 | 可空 | 默认值 | 扩展 | 注释 |
|---|---|---|---|---|---|
| id | bigint | NO | NULL | auto_increment | 存储主键ID |
| user_id | char(36) | NO | NULL |  | 归属用户ID |
| revision | bigint | NO | 0 |  | 纠正和删除推进的上下文失效代际 |
| creator_id | varchar(36) | NO |  |  | 创建人ID |
| creator_name | varchar(50) | NO |  |  | 创建人姓名 |
| create_time | datetime(3) | NO | CURRENT_TIMESTAMP(3) | DEFAULT_GENERATED | 创建时间 |
| last_updater_id | varchar(36) | NO |  |  | 最后更新人ID |
| last_updater_name | varchar(50) | NO |  |  | 最后更新人姓名 |
| last_update_time | datetime(3) | NO | CURRENT_TIMESTAMP(3) | DEFAULT_GENERATED on update CURRENT_TIMESTAMP(3) | 最后更新时间 |
| del_flag | char(1) | NO | 0 |  | 删除标志[0-否,1-是] |

索引、外键与完整建表定义（SHOW CREATE TABLE；仅结构）：

```sql
CREATE TABLE `sq_memory_revision` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '存储主键ID',
  `user_id` char(36) COLLATE utf8mb4_bin NOT NULL COMMENT '归属用户ID',
  `revision` bigint NOT NULL DEFAULT '0' COMMENT '纠正和删除推进的上下文失效代际',
  `creator_id` varchar(36) COLLATE utf8mb4_bin NOT NULL DEFAULT '' COMMENT '创建人ID',
  `creator_name` varchar(50) COLLATE utf8mb4_bin NOT NULL DEFAULT '' COMMENT '创建人姓名',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `last_updater_id` varchar(36) COLLATE utf8mb4_bin NOT NULL DEFAULT '' COMMENT '最后更新人ID',
  `last_updater_name` varchar(50) COLLATE utf8mb4_bin NOT NULL DEFAULT '' COMMENT '最后更新人姓名',
  `last_update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '最后更新时间',
  `del_flag` char(1) COLLATE utf8mb4_bin NOT NULL DEFAULT '0' COMMENT '删除标志[0-否,1-是]',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_memory_revision_user` (`user_id`)
) ENGINE=InnoDB AUTO_INCREMENT=348 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='用户记忆失效代际';
```

## sq_memory_scope

审计字段：完整七字段。

| 列 | 类型 | 可空 | 默认值 | 扩展 | 注释 |
|---|---|---|---|---|---|
| id | bigint | NO | NULL | auto_increment | 存储主键ID |
| user_id | char(36) | NO | NULL |  | 归属用户ID |
| scope_id | char(36) | NO | NULL |  | 受权执行作用域业务ID |
| memory_revision | bigint | NO | NULL |  | 冻结时用户记忆失效代际 |
| barrier_status | varchar(16) | NO | CURRENT |  | 上下文屏障[CURRENT,BLOCKED] |
| creator_id | varchar(36) | NO |  |  | 创建人ID |
| creator_name | varchar(50) | NO |  |  | 创建人姓名 |
| create_time | datetime(3) | NO | CURRENT_TIMESTAMP(3) | DEFAULT_GENERATED | 创建时间 |
| last_updater_id | varchar(36) | NO |  |  | 最后更新人ID |
| last_updater_name | varchar(50) | NO |  |  | 最后更新人姓名 |
| last_update_time | datetime(3) | NO | CURRENT_TIMESTAMP(3) | DEFAULT_GENERATED on update CURRENT_TIMESTAMP(3) | 最后更新时间 |
| del_flag | char(1) | NO | 0 |  | 删除标志[0-否,1-是] |

索引、外键与完整建表定义（SHOW CREATE TABLE；仅结构）：

```sql
CREATE TABLE `sq_memory_scope` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '存储主键ID',
  `user_id` char(36) COLLATE utf8mb4_bin NOT NULL COMMENT '归属用户ID',
  `scope_id` char(36) COLLATE utf8mb4_bin NOT NULL COMMENT '受权执行作用域业务ID',
  `memory_revision` bigint NOT NULL COMMENT '冻结时用户记忆失效代际',
  `barrier_status` varchar(16) COLLATE utf8mb4_bin NOT NULL DEFAULT 'CURRENT' COMMENT '上下文屏障[CURRENT,BLOCKED]',
  `creator_id` varchar(36) COLLATE utf8mb4_bin NOT NULL DEFAULT '' COMMENT '创建人ID',
  `creator_name` varchar(50) COLLATE utf8mb4_bin NOT NULL DEFAULT '' COMMENT '创建人姓名',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `last_updater_id` varchar(36) COLLATE utf8mb4_bin NOT NULL DEFAULT '' COMMENT '最后更新人ID',
  `last_updater_name` varchar(50) COLLATE utf8mb4_bin NOT NULL DEFAULT '' COMMENT '最后更新人姓名',
  `last_update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '最后更新时间',
  `del_flag` char(1) COLLATE utf8mb4_bin NOT NULL DEFAULT '0' COMMENT '删除标志[0-否,1-是]',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_memory_scope` (`user_id`,`scope_id`),
  CONSTRAINT `fk_memory_scope_owner` FOREIGN KEY (`user_id`, `scope_id`) REFERENCES `sq_execution_scope` (`user_id`, `scope_id`),
  CONSTRAINT `ck_memory_scope_barrier` CHECK ((`barrier_status` in (_utf8mb4'CURRENT',_utf8mb4'BLOCKED')))
) ENGINE=InnoDB AUTO_INCREMENT=3 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='执行固定的记忆授权视图';
```

## sq_memory_scope_item

审计字段：完整七字段。

| 列 | 类型 | 可空 | 默认值 | 扩展 | 注释 |
|---|---|---|---|---|---|
| id | bigint | NO | NULL | auto_increment | 存储主键ID |
| user_id | char(36) | NO | NULL |  | 归属用户ID |
| scope_id | char(36) | NO | NULL |  | 执行作用域业务ID |
| memory_id | char(36) | NO | NULL |  | 记忆业务ID |
| memory_version | bigint | NO | NULL |  | 冻结记忆内容版本 |
| creator_id | varchar(36) | NO |  |  | 创建人ID |
| creator_name | varchar(50) | NO |  |  | 创建人姓名 |
| create_time | datetime(3) | NO | CURRENT_TIMESTAMP(3) | DEFAULT_GENERATED | 创建时间 |
| last_updater_id | varchar(36) | NO |  |  | 最后更新人ID |
| last_updater_name | varchar(50) | NO |  |  | 最后更新人姓名 |
| last_update_time | datetime(3) | NO | CURRENT_TIMESTAMP(3) | DEFAULT_GENERATED on update CURRENT_TIMESTAMP(3) | 最后更新时间 |
| del_flag | char(1) | NO | 0 |  | 删除标志[0-否,1-是] |

索引、外键与完整建表定义（SHOW CREATE TABLE；仅结构）：

```sql
CREATE TABLE `sq_memory_scope_item` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '存储主键ID',
  `user_id` char(36) COLLATE utf8mb4_bin NOT NULL COMMENT '归属用户ID',
  `scope_id` char(36) COLLATE utf8mb4_bin NOT NULL COMMENT '执行作用域业务ID',
  `memory_id` char(36) COLLATE utf8mb4_bin NOT NULL COMMENT '记忆业务ID',
  `memory_version` bigint NOT NULL COMMENT '冻结记忆内容版本',
  `creator_id` varchar(36) COLLATE utf8mb4_bin NOT NULL DEFAULT '' COMMENT '创建人ID',
  `creator_name` varchar(50) COLLATE utf8mb4_bin NOT NULL DEFAULT '' COMMENT '创建人姓名',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `last_updater_id` varchar(36) COLLATE utf8mb4_bin NOT NULL DEFAULT '' COMMENT '最后更新人ID',
  `last_updater_name` varchar(50) COLLATE utf8mb4_bin NOT NULL DEFAULT '' COMMENT '最后更新人姓名',
  `last_update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '最后更新时间',
  `del_flag` char(1) COLLATE utf8mb4_bin NOT NULL DEFAULT '0' COMMENT '删除标志[0-否,1-是]',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_memory_scope_item` (`user_id`,`scope_id`,`memory_id`),
  KEY `fk_memory_item_record` (`user_id`,`memory_id`),
  CONSTRAINT `fk_memory_item_record` FOREIGN KEY (`user_id`, `memory_id`) REFERENCES `sq_memory` (`user_id`, `memory_id`),
  CONSTRAINT `fk_memory_item_scope` FOREIGN KEY (`user_id`, `scope_id`) REFERENCES `sq_memory_scope` (`user_id`, `scope_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='执行固定的记忆版本引用';
```

## sq_memory_source

审计字段：完整七字段。

| 列 | 类型 | 可空 | 默认值 | 扩展 | 注释 |
|---|---|---|---|---|---|
| id | bigint | NO | NULL | auto_increment | 存储主键ID |
| user_id | char(36) | NO | NULL |  | 归属用户ID |
| memory_id | char(36) | NO | NULL |  | 记忆业务ID |
| memory_version | bigint | NO | NULL |  | 显式授权时的记忆版本 |
| source_message_id | char(36) | NO | NULL |  | 用户显式授权原话来源ID，不复制正文 |
| status | varchar(16) | NO | ACTIVE |  | 来源状态[ACTIVE,INVALIDATED] |
| creator_id | varchar(36) | NO |  |  | 创建人ID |
| creator_name | varchar(50) | NO |  |  | 创建人姓名 |
| create_time | datetime(3) | NO | CURRENT_TIMESTAMP(3) | DEFAULT_GENERATED | 创建时间 |
| last_updater_id | varchar(36) | NO |  |  | 最后更新人ID |
| last_updater_name | varchar(50) | NO |  |  | 最后更新人姓名 |
| last_update_time | datetime(3) | NO | CURRENT_TIMESTAMP(3) | DEFAULT_GENERATED on update CURRENT_TIMESTAMP(3) | 最后更新时间 |
| del_flag | char(1) | NO | 0 |  | 删除标志[0-否,1-是] |

索引、外键与完整建表定义（SHOW CREATE TABLE；仅结构）：

```sql
CREATE TABLE `sq_memory_source` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '存储主键ID',
  `user_id` char(36) COLLATE utf8mb4_bin NOT NULL COMMENT '归属用户ID',
  `memory_id` char(36) COLLATE utf8mb4_bin NOT NULL COMMENT '记忆业务ID',
  `memory_version` bigint NOT NULL COMMENT '显式授权时的记忆版本',
  `source_message_id` char(36) COLLATE utf8mb4_bin NOT NULL COMMENT '用户显式授权原话来源ID，不复制正文',
  `status` varchar(16) COLLATE utf8mb4_bin NOT NULL DEFAULT 'ACTIVE' COMMENT '来源状态[ACTIVE,INVALIDATED]',
  `creator_id` varchar(36) COLLATE utf8mb4_bin NOT NULL DEFAULT '' COMMENT '创建人ID',
  `creator_name` varchar(50) COLLATE utf8mb4_bin NOT NULL DEFAULT '' COMMENT '创建人姓名',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `last_updater_id` varchar(36) COLLATE utf8mb4_bin NOT NULL DEFAULT '' COMMENT '最后更新人ID',
  `last_updater_name` varchar(50) COLLATE utf8mb4_bin NOT NULL DEFAULT '' COMMENT '最后更新人姓名',
  `last_update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '最后更新时间',
  `del_flag` char(1) COLLATE utf8mb4_bin NOT NULL DEFAULT '0' COMMENT '删除标志[0-否,1-是]',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_memory_source` (`user_id`,`memory_id`,`memory_version`,`source_message_id`),
  KEY `idx_memory_source_message` (`user_id`,`source_message_id`,`status`),
  CONSTRAINT `fk_memory_source_record` FOREIGN KEY (`user_id`, `memory_id`) REFERENCES `sq_memory` (`user_id`, `memory_id`),
  CONSTRAINT `ck_memory_source_status` CHECK ((`status` in (_utf8mb4'ACTIVE',_utf8mb4'INVALIDATED')))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='显式记忆原话来源和失效记录';
```

## sq_memory_write_receipt

审计字段：完整七字段。

| 列 | 类型 | 可空 | 默认值 | 扩展 | 注释 |
|---|---|---|---|---|---|
| id | bigint | NO | NULL | auto_increment | 存储主键ID |
| user_id | char(36) | NO | NULL |  | 归属用户ID |
| scope_id | char(36) | NO | NULL |  | 来源回复执行作用域业务ID |
| logical_key | varchar(160) | NO | NULL |  | 同执行内稳定工具逻辑键 |
| payload_hash | char(64) | NO | NULL |  | 规范化工具输入摘要 |
| result_json | json | NO | NULL |  | 工具持久收据，删除正文不在此重复保存 |
| creator_id | varchar(36) | NO |  |  | 创建人ID |
| creator_name | varchar(50) | NO |  |  | 创建人姓名 |
| create_time | datetime(3) | NO | CURRENT_TIMESTAMP(3) | DEFAULT_GENERATED | 创建时间 |
| last_updater_id | varchar(36) | NO |  |  | 最后更新人ID |
| last_updater_name | varchar(50) | NO |  |  | 最后更新人姓名 |
| last_update_time | datetime(3) | NO | CURRENT_TIMESTAMP(3) | DEFAULT_GENERATED on update CURRENT_TIMESTAMP(3) | 最后更新时间 |
| del_flag | char(1) | NO | 0 |  | 删除标志[0-否,1-是] |

索引、外键与完整建表定义（SHOW CREATE TABLE；仅结构）：

```sql
CREATE TABLE `sq_memory_write_receipt` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '存储主键ID',
  `user_id` char(36) COLLATE utf8mb4_bin NOT NULL COMMENT '归属用户ID',
  `scope_id` char(36) COLLATE utf8mb4_bin NOT NULL COMMENT '来源回复执行作用域业务ID',
  `logical_key` varchar(160) COLLATE utf8mb4_bin NOT NULL COMMENT '同执行内稳定工具逻辑键',
  `payload_hash` char(64) COLLATE utf8mb4_bin NOT NULL COMMENT '规范化工具输入摘要',
  `result_json` json NOT NULL COMMENT '工具持久收据，删除正文不在此重复保存',
  `creator_id` varchar(36) COLLATE utf8mb4_bin NOT NULL DEFAULT '' COMMENT '创建人ID',
  `creator_name` varchar(50) COLLATE utf8mb4_bin NOT NULL DEFAULT '' COMMENT '创建人姓名',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `last_updater_id` varchar(36) COLLATE utf8mb4_bin NOT NULL DEFAULT '' COMMENT '最后更新人ID',
  `last_updater_name` varchar(50) COLLATE utf8mb4_bin NOT NULL DEFAULT '' COMMENT '最后更新人姓名',
  `last_update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '最后更新时间',
  `del_flag` char(1) COLLATE utf8mb4_bin NOT NULL DEFAULT '0' COMMENT '删除标志[0-否,1-是]',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_memory_write_receipt` (`user_id`,`scope_id`,`logical_key`),
  CONSTRAINT `fk_memory_receipt_scope` FOREIGN KEY (`user_id`, `scope_id`) REFERENCES `sq_execution_scope` (`user_id`, `scope_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='记忆工具的持久幂等收据';
```

## sq_message

审计字段：完整七字段。

| 列 | 类型 | 可空 | 默认值 | 扩展 | 注释 |
|---|---|---|---|---|---|
| id | bigint | NO | NULL | auto_increment | 存储主键ID |
| message_id | char(36) | NO | NULL |  | 消息业务ID |
| user_id | char(36) | NO | NULL |  | 资源归属用户ID |
| conversation_id | char(36) | NO | NULL |  | 所属会话ID |
| message_seq | bigint | NO | NULL |  | 会话内递增消息序号 |
| role | varchar(16) | NO | NULL |  | 消息角色[USER,ASSISTANT] |
| content | longtext | NO | NULL |  | 用户消息或授权助手正文 |
| revision | bigint | NO | 0 |  | 正文替换版本 |
| completed | tinyint(1) | NO | 0 |  | 正文是否完成 |
| source_message_id | char(36) | YES | NULL |  | 回复所关联的用户消息ID |
| execution_id | char(36) | YES | NULL |  | 回复执行ID；旧任务消息为空 |
| task_id | char(36) | YES | NULL |  | 可选关联目标ID |
| occurred_at | datetime(3) | NO | NULL |  | 消息发生时间 |
| legacy_key | varchar(100) | YES | NULL |  | 旧记录回填稳定键；新消息为空 |
| creator_id | varchar(36) | NO |  |  | 创建人ID |
| creator_name | varchar(50) | NO |  |  | 创建人姓名 |
| create_time | datetime(3) | NO | CURRENT_TIMESTAMP(3) | DEFAULT_GENERATED | 创建时间 |
| last_updater_id | varchar(36) | NO |  |  | 最后更新人ID |
| last_updater_name | varchar(50) | NO |  |  | 最后更新人姓名 |
| last_update_time | datetime(3) | NO | CURRENT_TIMESTAMP(3) | DEFAULT_GENERATED on update CURRENT_TIMESTAMP(3) | 最后更新时间 |
| del_flag | char(1) | NO | 0 |  | 删除标志[0-否,1-是] |

索引、外键与完整建表定义（SHOW CREATE TABLE；仅结构）：

```sql
CREATE TABLE `sq_message` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '存储主键ID',
  `message_id` char(36) COLLATE utf8mb4_bin NOT NULL COMMENT '消息业务ID',
  `user_id` char(36) COLLATE utf8mb4_bin NOT NULL COMMENT '资源归属用户ID',
  `conversation_id` char(36) COLLATE utf8mb4_bin NOT NULL COMMENT '所属会话ID',
  `message_seq` bigint NOT NULL COMMENT '会话内递增消息序号',
  `role` varchar(16) COLLATE utf8mb4_bin NOT NULL COMMENT '消息角色[USER,ASSISTANT]',
  `content` longtext COLLATE utf8mb4_bin NOT NULL COMMENT '用户消息或授权助手正文',
  `revision` bigint NOT NULL DEFAULT '0' COMMENT '正文替换版本',
  `completed` tinyint(1) NOT NULL DEFAULT '0' COMMENT '正文是否完成',
  `source_message_id` char(36) COLLATE utf8mb4_bin DEFAULT NULL COMMENT '回复所关联的用户消息ID',
  `execution_id` char(36) COLLATE utf8mb4_bin DEFAULT NULL COMMENT '回复执行ID；旧任务消息为空',
  `task_id` char(36) COLLATE utf8mb4_bin DEFAULT NULL COMMENT '可选关联目标ID',
  `occurred_at` datetime(3) NOT NULL COMMENT '消息发生时间',
  `legacy_key` varchar(100) COLLATE utf8mb4_bin DEFAULT NULL COMMENT '旧记录回填稳定键；新消息为空',
  `creator_id` varchar(36) COLLATE utf8mb4_bin NOT NULL DEFAULT '' COMMENT '创建人ID',
  `creator_name` varchar(50) COLLATE utf8mb4_bin NOT NULL DEFAULT '' COMMENT '创建人姓名',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `last_updater_id` varchar(36) COLLATE utf8mb4_bin NOT NULL DEFAULT '' COMMENT '最后更新人ID',
  `last_updater_name` varchar(50) COLLATE utf8mb4_bin NOT NULL DEFAULT '' COMMENT '最后更新人姓名',
  `last_update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '最后更新时间',
  `del_flag` char(1) COLLATE utf8mb4_bin NOT NULL DEFAULT '0' COMMENT '删除标志[0-否,1-是]',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_message` (`user_id`,`message_id`),
  UNIQUE KEY `uk_conversation_message_seq` (`user_id`,`conversation_id`,`message_seq`),
  UNIQUE KEY `uk_message_legacy` (`user_id`,`legacy_key`),
  KEY `fk_message_task` (`user_id`,`task_id`),
  CONSTRAINT `fk_message_conversation` FOREIGN KEY (`user_id`, `conversation_id`) REFERENCES `sq_conversation` (`user_id`, `conversation_id`),
  CONSTRAINT `fk_message_task` FOREIGN KEY (`user_id`, `task_id`) REFERENCES `sq_goal_task` (`user_id`, `task_id`),
  CONSTRAINT `ck_message_del` CHECK ((`del_flag` in (_utf8mb4'0',_utf8mb4'1'))),
  CONSTRAINT `ck_message_role` CHECK ((`role` in (_utf8mb4'USER',_utf8mb4'ASSISTANT'))),
  CONSTRAINT `ck_message_seq` CHECK (((`message_seq` > 0) and (`revision` >= 0)))
) ENGINE=InnoDB AUTO_INCREMENT=4 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='持久用户与助手消息账本';
```

## sq_message_artifact

审计字段：完整七字段。

| 列 | 类型 | 可空 | 默认值 | 扩展 | 注释 |
|---|---|---|---|---|---|
| id | bigint | NO | NULL | auto_increment | 存储主键ID |
| user_id | char(36) | NO | NULL |  | 资源归属用户ID |
| message_id | char(36) | NO | NULL |  | 消息ID |
| artifact_id | char(36) | NO | NULL |  | 输入或交付产物ID |
| creator_id | varchar(36) | NO |  |  | 创建人ID |
| creator_name | varchar(50) | NO |  |  | 创建人姓名 |
| create_time | datetime(3) | NO | CURRENT_TIMESTAMP(3) | DEFAULT_GENERATED | 创建时间 |
| last_updater_id | varchar(36) | NO |  |  | 最后更新人ID |
| last_updater_name | varchar(50) | NO |  |  | 最后更新人姓名 |
| last_update_time | datetime(3) | NO | CURRENT_TIMESTAMP(3) | DEFAULT_GENERATED on update CURRENT_TIMESTAMP(3) | 最后更新时间 |
| del_flag | char(1) | NO | 0 |  | 删除标志[0-否,1-是] |

索引、外键与完整建表定义（SHOW CREATE TABLE；仅结构）：

```sql
CREATE TABLE `sq_message_artifact` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '存储主键ID',
  `user_id` char(36) COLLATE utf8mb4_bin NOT NULL COMMENT '资源归属用户ID',
  `message_id` char(36) COLLATE utf8mb4_bin NOT NULL COMMENT '消息ID',
  `artifact_id` char(36) COLLATE utf8mb4_bin NOT NULL COMMENT '输入或交付产物ID',
  `creator_id` varchar(36) COLLATE utf8mb4_bin NOT NULL DEFAULT '' COMMENT '创建人ID',
  `creator_name` varchar(50) COLLATE utf8mb4_bin NOT NULL DEFAULT '' COMMENT '创建人姓名',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `last_updater_id` varchar(36) COLLATE utf8mb4_bin NOT NULL DEFAULT '' COMMENT '最后更新人ID',
  `last_updater_name` varchar(50) COLLATE utf8mb4_bin NOT NULL DEFAULT '' COMMENT '最后更新人姓名',
  `last_update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '最后更新时间',
  `del_flag` char(1) COLLATE utf8mb4_bin NOT NULL DEFAULT '0' COMMENT '删除标志[0-否,1-是]',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_message_artifact` (`user_id`,`message_id`,`artifact_id`),
  KEY `fk_message_artifact_owner` (`user_id`,`artifact_id`),
  CONSTRAINT `fk_message_artifact_message` FOREIGN KEY (`user_id`, `message_id`) REFERENCES `sq_message` (`user_id`, `message_id`),
  CONSTRAINT `fk_message_artifact_owner` FOREIGN KEY (`user_id`, `artifact_id`) REFERENCES `sq_artifact` (`user_id`, `artifact_id`),
  CONSTRAINT `ck_message_artifact_del` CHECK ((`del_flag` in (_utf8mb4'0',_utf8mb4'1')))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='消息与产物归属关联';
```

## sq_mock_ticket

审计字段：完整七字段。

| 列 | 类型 | 可空 | 默认值 | 扩展 | 注释 |
|---|---|---|---|---|---|
| id | bigint | NO | NULL | auto_increment | 存储主键ID |
| user_id | char(36) | NO | NULL |  | 资源归属用户ID |
| external_key | varchar(200) | NO | NULL |  | 外部幂等键；同键同内容复用原单 |
| request_hash | varchar(64) | NO | NULL |  | 请求参数摘要 |
| title | varchar(200) | NO | NULL |  | 工单标题 |
| detail | varchar(2000) | NO | NULL |  | 工单描述 |
| creator_id | varchar(36) | NO |  |  | 创建人ID |
| creator_name | varchar(50) | NO |  |  | 创建人姓名 |
| create_time | datetime(3) | NO | CURRENT_TIMESTAMP(3) | DEFAULT_GENERATED | 创建时间 |
| last_updater_id | varchar(36) | NO |  |  | 最后更新人ID |
| last_updater_name | varchar(50) | NO |  |  | 最后更新人姓名 |
| last_update_time | datetime(3) | NO | CURRENT_TIMESTAMP(3) | DEFAULT_GENERATED on update CURRENT_TIMESTAMP(3) | 最后更新时间 |
| del_flag | char(1) | NO | 0 |  | 删除标志[0-否,1-是] |

索引、外键与完整建表定义（SHOW CREATE TABLE；仅结构）：

```sql
CREATE TABLE `sq_mock_ticket` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '存储主键ID',
  `user_id` char(36) COLLATE utf8mb4_bin NOT NULL COMMENT '资源归属用户ID',
  `external_key` varchar(200) COLLATE utf8mb4_bin NOT NULL COMMENT '外部幂等键；同键同内容复用原单',
  `request_hash` varchar(64) COLLATE utf8mb4_bin NOT NULL COMMENT '请求参数摘要',
  `title` varchar(200) COLLATE utf8mb4_bin NOT NULL COMMENT '工单标题',
  `detail` varchar(2000) COLLATE utf8mb4_bin NOT NULL COMMENT '工单描述',
  `creator_id` varchar(36) COLLATE utf8mb4_bin NOT NULL DEFAULT '' COMMENT '创建人ID',
  `creator_name` varchar(50) COLLATE utf8mb4_bin NOT NULL DEFAULT '' COMMENT '创建人姓名',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `last_updater_id` varchar(36) COLLATE utf8mb4_bin NOT NULL DEFAULT '' COMMENT '最后更新人ID',
  `last_updater_name` varchar(50) COLLATE utf8mb4_bin NOT NULL DEFAULT '' COMMENT '最后更新人姓名',
  `last_update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '最后更新时间',
  `del_flag` char(1) COLLATE utf8mb4_bin NOT NULL DEFAULT '0' COMMENT '删除标志[0-否,1-是]',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_mock_ticket_key` (`user_id`,`external_key`),
  CONSTRAINT `ck_mock_ticket_del` CHECK ((`del_flag` in (_utf8mb4'0',_utf8mb4'1')))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='本地模拟外部工单系统';
```

## sq_operation

审计字段：完整七字段。

| 列 | 类型 | 可空 | 默认值 | 扩展 | 注释 |
|---|---|---|---|---|---|
| id | bigint | NO | NULL | auto_increment | 存储主键ID |
| operation_id | char(36) | NO | NULL |  | 操作ID |
| user_id | char(36) | NO | NULL |  | 资源归属用户ID |
| task_id | char(36) | YES | NULL |  | 可选所属目标ID；普通回复为空 |
| job_id | char(36) | YES | NULL |  | 节点ID；Master可空 |
| binding_id | char(36) | NO | NULL |  | 原生调用所属绑定 |
| framework_turn_id | varchar(200) | NO | NULL |  | 原生TurnID |
| tool_call_id | varchar(200) | NO | NULL |  | 原生稳定工具调用ID |
| tool_key | varchar(100) | NO | NULL |  | 工具键 |
| tool_version | varchar(40) | NO | NULL |  | 工具版本 |
| business_intent_key | varchar(160) | YES | NULL |  | 业务写入幂等键；READ可空 |
| effect | varchar(16) | NO | NULL |  | READ/WRITE/SANDBOX |
| connection_id | char(36) | YES | NULL |  | 用户连接ID |
| connection_revision | bigint | NO | 0 |  | 派发时连接版本 |
| auth_version | bigint | NO | 0 |  | 派发时授权版本 |
| request_hash | varchar(64) | NO | NULL |  | 规范化参数摘要 |
| arguments_json | json | NO | NULL |  | 脱敏参数；敏感值用引用 |
| status | varchar(24) | NO | NULL |  | PREPARED/WAITING_APPROVAL/IN_FLIGHT/UNKNOWN/RECONCILING/SUCCEEDED/FAILED/CANCELLED |
| version | bigint | NO | 0 |  | 业务乐观版本 |
| executor_owner | varchar(100) | YES | NULL |  | 独立执行器实例 |
| executor_fence | bigint | NO | 0 |  | 独立执行器栅栏 |
| executor_until | datetime(3) | YES | NULL |  | 执行器租约 |
| external_key | varchar(200) | YES | NULL |  | 外部幂等或查询关联号 |
| result_json | json | YES | NULL |  | 可信连接器结果或核对事实 |
| creator_id | varchar(36) | NO |  |  | 创建人ID |
| creator_name | varchar(50) | NO |  |  | 创建人姓名 |
| create_time | datetime(3) | NO | CURRENT_TIMESTAMP(3) | DEFAULT_GENERATED | 创建时间 |
| last_updater_id | varchar(36) | NO |  |  | 最后更新人ID |
| last_updater_name | varchar(50) | NO |  |  | 最后更新人姓名 |
| last_update_time | datetime(3) | NO | CURRENT_TIMESTAMP(3) | DEFAULT_GENERATED on update CURRENT_TIMESTAMP(3) | 最后更新时间 |
| del_flag | char(1) | NO | 0 |  | 删除标志[0-否,1-是] |
| scope_id | char(36) | YES | NULL |  | 统一执行作用域ID |
| budget_usage_key | varchar(200) | YES | NULL |  | 该操作固定的预算预留稳定键 |

索引、外键与完整建表定义（SHOW CREATE TABLE；仅结构）：

```sql
CREATE TABLE `sq_operation` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '存储主键ID',
  `operation_id` char(36) COLLATE utf8mb4_bin NOT NULL COMMENT '操作ID',
  `user_id` char(36) COLLATE utf8mb4_bin NOT NULL COMMENT '资源归属用户ID',
  `task_id` char(36) COLLATE utf8mb4_bin DEFAULT NULL COMMENT '可选所属目标ID；普通回复为空',
  `job_id` char(36) COLLATE utf8mb4_bin DEFAULT NULL COMMENT '节点ID；Master可空',
  `binding_id` char(36) COLLATE utf8mb4_bin NOT NULL COMMENT '原生调用所属绑定',
  `framework_turn_id` varchar(200) COLLATE utf8mb4_bin NOT NULL COMMENT '原生TurnID',
  `tool_call_id` varchar(200) COLLATE utf8mb4_bin NOT NULL COMMENT '原生稳定工具调用ID',
  `tool_key` varchar(100) COLLATE utf8mb4_bin NOT NULL COMMENT '工具键',
  `tool_version` varchar(40) COLLATE utf8mb4_bin NOT NULL COMMENT '工具版本',
  `business_intent_key` varchar(160) COLLATE utf8mb4_bin DEFAULT NULL COMMENT '业务写入幂等键；READ可空',
  `effect` varchar(16) COLLATE utf8mb4_bin NOT NULL COMMENT 'READ/WRITE/SANDBOX',
  `connection_id` char(36) COLLATE utf8mb4_bin DEFAULT NULL COMMENT '用户连接ID',
  `connection_revision` bigint NOT NULL DEFAULT '0' COMMENT '派发时连接版本',
  `auth_version` bigint NOT NULL DEFAULT '0' COMMENT '派发时授权版本',
  `request_hash` varchar(64) COLLATE utf8mb4_bin NOT NULL COMMENT '规范化参数摘要',
  `arguments_json` json NOT NULL COMMENT '脱敏参数；敏感值用引用',
  `status` varchar(24) COLLATE utf8mb4_bin NOT NULL COMMENT 'PREPARED/WAITING_APPROVAL/IN_FLIGHT/UNKNOWN/RECONCILING/SUCCEEDED/FAILED/CANCELLED',
  `version` bigint NOT NULL DEFAULT '0' COMMENT '业务乐观版本',
  `executor_owner` varchar(100) COLLATE utf8mb4_bin DEFAULT NULL COMMENT '独立执行器实例',
  `executor_fence` bigint NOT NULL DEFAULT '0' COMMENT '独立执行器栅栏',
  `executor_until` datetime(3) DEFAULT NULL COMMENT '执行器租约',
  `external_key` varchar(200) COLLATE utf8mb4_bin DEFAULT NULL COMMENT '外部幂等或查询关联号',
  `result_json` json DEFAULT NULL COMMENT '可信连接器结果或核对事实',
  `creator_id` varchar(36) COLLATE utf8mb4_bin NOT NULL DEFAULT '' COMMENT '创建人ID',
  `creator_name` varchar(50) COLLATE utf8mb4_bin NOT NULL DEFAULT '' COMMENT '创建人姓名',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `last_updater_id` varchar(36) COLLATE utf8mb4_bin NOT NULL DEFAULT '' COMMENT '最后更新人ID',
  `last_updater_name` varchar(50) COLLATE utf8mb4_bin NOT NULL DEFAULT '' COMMENT '最后更新人姓名',
  `last_update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '最后更新时间',
  `del_flag` char(1) COLLATE utf8mb4_bin NOT NULL DEFAULT '0' COMMENT '删除标志[0-否,1-是]',
  `scope_id` char(36) COLLATE utf8mb4_bin DEFAULT NULL COMMENT '统一执行作用域ID',
  `budget_usage_key` varchar(200) COLLATE utf8mb4_bin DEFAULT NULL COMMENT '该操作固定的预算预留稳定键',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_owner_operation` (`user_id`,`operation_id`),
  UNIQUE KEY `uk_native_call` (`user_id`,`binding_id`,`framework_turn_id`,`tool_call_id`),
  UNIQUE KEY `uk_business_intent` (`user_id`,`task_id`,`tool_key`,`business_intent_key`),
  UNIQUE KEY `uk_scope_business_intent` (`user_id`,`scope_id`,`tool_key`,`business_intent_key`),
  KEY `idx_operation_unknown` (`status`,`last_update_time`),
  CONSTRAINT `fk_operation_scope` FOREIGN KEY (`user_id`, `scope_id`) REFERENCES `sq_execution_scope` (`user_id`, `scope_id`),
  CONSTRAINT `ck_operation_del` CHECK ((`del_flag` in (_utf8mb4'0',_utf8mb4'1')))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='外部工具副作用账本';
```

## sq_operation_reconciler

审计字段：完整七字段。

| 列 | 类型 | 可空 | 默认值 | 扩展 | 注释 |
|---|---|---|---|---|---|
| id | bigint | NO | NULL | auto_increment | 存储主键ID |
| user_id | char(36) | NO | NULL |  | 被授权核对用户ID |
| task_id | char(36) | YES | NULL |  | 可选核对目标上下文 |
| creator_id | varchar(36) | NO |  |  | 创建人ID |
| creator_name | varchar(50) | NO |  |  | 创建人姓名 |
| create_time | datetime(3) | NO | CURRENT_TIMESTAMP(3) | DEFAULT_GENERATED | 创建时间 |
| last_updater_id | varchar(36) | NO |  |  | 最后更新人ID |
| last_updater_name | varchar(50) | NO |  |  | 最后更新人姓名 |
| last_update_time | datetime(3) | NO | CURRENT_TIMESTAMP(3) | DEFAULT_GENERATED on update CURRENT_TIMESTAMP(3) | 最后更新时间 |
| del_flag | char(1) | NO | 0 |  | 删除标志[0-否,1-是] |
| scope_id | char(36) | YES | NULL |  | 核对授权执行作用域 |

索引、外键与完整建表定义（SHOW CREATE TABLE；仅结构）：

```sql
CREATE TABLE `sq_operation_reconciler` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '存储主键ID',
  `user_id` char(36) COLLATE utf8mb4_bin NOT NULL COMMENT '被授权核对用户ID',
  `task_id` char(36) COLLATE utf8mb4_bin DEFAULT NULL COMMENT '可选核对目标上下文',
  `creator_id` varchar(36) COLLATE utf8mb4_bin NOT NULL DEFAULT '' COMMENT '创建人ID',
  `creator_name` varchar(50) COLLATE utf8mb4_bin NOT NULL DEFAULT '' COMMENT '创建人姓名',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `last_updater_id` varchar(36) COLLATE utf8mb4_bin NOT NULL DEFAULT '' COMMENT '最后更新人ID',
  `last_updater_name` varchar(50) COLLATE utf8mb4_bin NOT NULL DEFAULT '' COMMENT '最后更新人姓名',
  `last_update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '最后更新时间',
  `del_flag` char(1) COLLATE utf8mb4_bin NOT NULL DEFAULT '0' COMMENT '删除标志[0-否,1-是]',
  `scope_id` char(36) COLLATE utf8mb4_bin DEFAULT NULL COMMENT '核对授权执行作用域',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_operation_reconciler` (`user_id`,`task_id`),
  UNIQUE KEY `uk_reconciler_scope` (`user_id`,`scope_id`),
  CONSTRAINT `fk_reconciler_scope` FOREIGN KEY (`user_id`, `scope_id`) REFERENCES `sq_execution_scope` (`user_id`, `scope_id`),
  CONSTRAINT `fk_reconciler_task` FOREIGN KEY (`user_id`, `task_id`) REFERENCES `sq_goal_task` (`user_id`, `task_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='操作核对显式授权范围';
```

## sq_plan

审计字段：完整七字段。

| 列 | 类型 | 可空 | 默认值 | 扩展 | 注释 |
|---|---|---|---|---|---|
| id | bigint | NO | NULL | auto_increment | 存储主键ID |
| plan_id | char(36) | NO | NULL |  | 计划业务ID |
| user_id | char(36) | NO | NULL |  | 资源归属用户ID |
| task_id | char(36) | NO | NULL |  | 所属目标ID |
| plan_version | bigint | NO | NULL |  | 计划版本（taskId 内递增） |
| logical_key | varchar(160) | NO | NULL |  | 计划稳定逻辑键（幂等重放） |
| status | varchar(16) | NO | NULL |  | ACTIVE/SUPERSEDED |
| definition_json | json | NO | NULL |  | 不可变计划定义：节点/依赖/汇合标准/能力与权限版本引用 |
| signal_version | bigint | NO | 0 |  | 汇合信号版本 |
| version | bigint | NO | 0 |  | 业务版本 |
| creator_id | varchar(36) | NO |  |  | 创建人ID |
| creator_name | varchar(50) | NO |  |  | 创建人姓名 |
| create_time | datetime(3) | NO | CURRENT_TIMESTAMP(3) | DEFAULT_GENERATED | 创建时间 |
| last_updater_id | varchar(36) | NO |  |  | 最后更新人ID |
| last_updater_name | varchar(50) | NO |  |  | 最后更新人姓名 |
| last_update_time | datetime(3) | NO | CURRENT_TIMESTAMP(3) | DEFAULT_GENERATED on update CURRENT_TIMESTAMP(3) | 最后更新时间 |
| del_flag | char(1) | NO | 0 |  | 删除标志[0-否,1-是] |
| goal_revision | bigint | NO | 0 |  | 发布计划时的目标修订号 |

索引、外键与完整建表定义（SHOW CREATE TABLE；仅结构）：

```sql
CREATE TABLE `sq_plan` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '存储主键ID',
  `plan_id` char(36) COLLATE utf8mb4_bin NOT NULL COMMENT '计划业务ID',
  `user_id` char(36) COLLATE utf8mb4_bin NOT NULL COMMENT '资源归属用户ID',
  `task_id` char(36) COLLATE utf8mb4_bin NOT NULL COMMENT '所属目标ID',
  `plan_version` bigint NOT NULL COMMENT '计划版本（taskId 内递增）',
  `logical_key` varchar(160) COLLATE utf8mb4_bin NOT NULL COMMENT '计划稳定逻辑键（幂等重放）',
  `status` varchar(16) COLLATE utf8mb4_bin NOT NULL COMMENT 'ACTIVE/SUPERSEDED',
  `definition_json` json NOT NULL COMMENT '不可变计划定义：节点/依赖/汇合标准/能力与权限版本引用',
  `signal_version` bigint NOT NULL DEFAULT '0' COMMENT '汇合信号版本',
  `version` bigint NOT NULL DEFAULT '0' COMMENT '业务版本',
  `creator_id` varchar(36) COLLATE utf8mb4_bin NOT NULL DEFAULT '' COMMENT '创建人ID',
  `creator_name` varchar(50) COLLATE utf8mb4_bin NOT NULL DEFAULT '' COMMENT '创建人姓名',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `last_updater_id` varchar(36) COLLATE utf8mb4_bin NOT NULL DEFAULT '' COMMENT '最后更新人ID',
  `last_updater_name` varchar(50) COLLATE utf8mb4_bin NOT NULL DEFAULT '' COMMENT '最后更新人姓名',
  `last_update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '最后更新时间',
  `del_flag` char(1) COLLATE utf8mb4_bin NOT NULL DEFAULT '0' COMMENT '删除标志[0-否,1-是]',
  `goal_revision` bigint NOT NULL DEFAULT '0' COMMENT '发布计划时的目标修订号',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_plan` (`plan_id`),
  UNIQUE KEY `uk_plan_task_version` (`task_id`,`plan_version`),
  UNIQUE KEY `uk_plan_task_logical` (`task_id`,`logical_key`),
  KEY `idx_plan_task` (`task_id`),
  KEY `fk_plan_task` (`user_id`,`task_id`),
  CONSTRAINT `fk_plan_task` FOREIGN KEY (`user_id`, `task_id`) REFERENCES `sq_goal_task` (`user_id`, `task_id`),
  CONSTRAINT `ck_plan_del` CHECK ((`del_flag` in (_utf8mb4'0',_utf8mb4'1'))),
  CONSTRAINT `ck_plan_status` CHECK ((`status` in (_utf8mb4'ACTIVE',_utf8mb4'SUPERSEDED')))
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='不可变协作计划DAG';
```

## sq_response_execution

审计字段：完整七字段。

| 列 | 类型 | 可空 | 默认值 | 扩展 | 注释 |
|---|---|---|---|---|---|
| id | bigint | NO | NULL | auto_increment | 存储主键ID |
| execution_id | char(36) | NO | NULL |  | 回复执行业务ID |
| user_id | char(36) | NO | NULL |  | 资源归属用户ID |
| conversation_id | char(36) | NO | NULL |  | 所属会话ID |
| source_message_id | char(36) | NO | NULL |  | 来源用户消息ID |
| scope_id | char(36) | NO | NULL |  | 回复执行作用域ID |
| binding_id | char(36) | NO | NULL |  | 固定原生绑定ID |
| status | varchar(24) | NO | NULL |  | 回复生命周期状态 |
| version | bigint | NO | 0 |  | 业务乐观版本 |
| answer_message_id | char(36) | YES | NULL |  | 授权助手回答消息ID |
| cancel_requested_at | datetime(3) | YES | NULL |  | 停止回答意图时间 |
| completion_proposal_json | json | YES | NULL |  | 待原生成功后裁决的完成候选 |
| proposal_turn_id | varchar(64) | YES | NULL |  | 完成候选来源原生TurnID |
| proposal_hash | char(64) | YES | NULL |  | 完成候选规范化摘要 |
| correction_count | int | NO | 0 |  | 无效完成候选纠正次数 |
| recovery_attempts | int | NO | 0 |  | 已持久化的自动恢复次数 |
| next_attempt_at | datetime(3) | YES | NULL |  | 下次自动恢复时间 |
| failure_id | char(36) | YES | NULL |  | 最近结构化故障ID |
| retry_of_execution_id | char(36) | YES | NULL |  | 终态重试的前序执行ID |
| creator_id | varchar(36) | NO |  |  | 创建人ID |
| creator_name | varchar(50) | NO |  |  | 创建人姓名 |
| create_time | datetime(3) | NO | CURRENT_TIMESTAMP(3) | DEFAULT_GENERATED | 创建时间 |
| last_updater_id | varchar(36) | NO |  |  | 最后更新人ID |
| last_updater_name | varchar(50) | NO |  |  | 最后更新人姓名 |
| last_update_time | datetime(3) | NO | CURRENT_TIMESTAMP(3) | DEFAULT_GENERATED on update CURRENT_TIMESTAMP(3) | 最后更新时间 |
| del_flag | char(1) | NO | 0 |  | 删除标志[0-否,1-是] |
| context_json | longtext | YES | NULL |  | 冻结的授权会话消息前缀 |
| context_hash | char(64) | YES | NULL |  | 冻结前缀SHA256摘要 |
| dispatch_sequence | bigint | YES | NULL |  | 回复领取顺序，显式重试排在已受理执行之后 |

索引、外键与完整建表定义（SHOW CREATE TABLE；仅结构）：

```sql
CREATE TABLE `sq_response_execution` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '存储主键ID',
  `execution_id` char(36) COLLATE utf8mb4_bin NOT NULL COMMENT '回复执行业务ID',
  `user_id` char(36) COLLATE utf8mb4_bin NOT NULL COMMENT '资源归属用户ID',
  `conversation_id` char(36) COLLATE utf8mb4_bin NOT NULL COMMENT '所属会话ID',
  `source_message_id` char(36) COLLATE utf8mb4_bin NOT NULL COMMENT '来源用户消息ID',
  `scope_id` char(36) COLLATE utf8mb4_bin NOT NULL COMMENT '回复执行作用域ID',
  `binding_id` char(36) COLLATE utf8mb4_bin NOT NULL COMMENT '固定原生绑定ID',
  `status` varchar(24) COLLATE utf8mb4_bin NOT NULL COMMENT '回复生命周期状态',
  `version` bigint NOT NULL DEFAULT '0' COMMENT '业务乐观版本',
  `answer_message_id` char(36) COLLATE utf8mb4_bin DEFAULT NULL COMMENT '授权助手回答消息ID',
  `cancel_requested_at` datetime(3) DEFAULT NULL COMMENT '停止回答意图时间',
  `completion_proposal_json` json DEFAULT NULL COMMENT '待原生成功后裁决的完成候选',
  `proposal_turn_id` varchar(64) COLLATE utf8mb4_bin DEFAULT NULL COMMENT '完成候选来源原生TurnID',
  `proposal_hash` char(64) COLLATE utf8mb4_bin DEFAULT NULL COMMENT '完成候选规范化摘要',
  `correction_count` int NOT NULL DEFAULT '0' COMMENT '无效完成候选纠正次数',
  `recovery_attempts` int NOT NULL DEFAULT '0' COMMENT '已持久化的自动恢复次数',
  `next_attempt_at` datetime(3) DEFAULT NULL COMMENT '下次自动恢复时间',
  `failure_id` char(36) COLLATE utf8mb4_bin DEFAULT NULL COMMENT '最近结构化故障ID',
  `retry_of_execution_id` char(36) COLLATE utf8mb4_bin DEFAULT NULL COMMENT '终态重试的前序执行ID',
  `creator_id` varchar(36) COLLATE utf8mb4_bin NOT NULL DEFAULT '' COMMENT '创建人ID',
  `creator_name` varchar(50) COLLATE utf8mb4_bin NOT NULL DEFAULT '' COMMENT '创建人姓名',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `last_updater_id` varchar(36) COLLATE utf8mb4_bin NOT NULL DEFAULT '' COMMENT '最后更新人ID',
  `last_updater_name` varchar(50) COLLATE utf8mb4_bin NOT NULL DEFAULT '' COMMENT '最后更新人姓名',
  `last_update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '最后更新时间',
  `del_flag` char(1) COLLATE utf8mb4_bin NOT NULL DEFAULT '0' COMMENT '删除标志[0-否,1-是]',
  `context_json` longtext COLLATE utf8mb4_bin COMMENT '冻结的授权会话消息前缀',
  `context_hash` char(64) COLLATE utf8mb4_bin DEFAULT NULL COMMENT '冻结前缀SHA256摘要',
  `dispatch_sequence` bigint DEFAULT NULL COMMENT '回复领取顺序，显式重试排在已受理执行之后',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_response_execution` (`user_id`,`execution_id`),
  UNIQUE KEY `uk_response_scope` (`user_id`,`scope_id`),
  UNIQUE KEY `uk_response_binding` (`user_id`,`binding_id`),
  UNIQUE KEY `uk_response_successor` (`user_id`,`retry_of_execution_id`),
  KEY `idx_response_conversation` (`user_id`,`conversation_id`,`id`),
  KEY `fk_response_message` (`user_id`,`source_message_id`),
  CONSTRAINT `fk_response_conversation` FOREIGN KEY (`user_id`, `conversation_id`) REFERENCES `sq_conversation` (`user_id`, `conversation_id`),
  CONSTRAINT `fk_response_message` FOREIGN KEY (`user_id`, `source_message_id`) REFERENCES `sq_message` (`user_id`, `message_id`),
  CONSTRAINT `fk_response_scope` FOREIGN KEY (`user_id`, `scope_id`) REFERENCES `sq_execution_scope` (`user_id`, `scope_id`),
  CONSTRAINT `ck_response_del` CHECK ((`del_flag` in (_utf8mb4'0',_utf8mb4'1'))),
  CONSTRAINT `ck_response_status` CHECK ((`status` in (_utf8mb4'QUEUED',_utf8mb4'RUNNING',_utf8mb4'WAITING_INPUT',_utf8mb4'WAITING_APPROVAL',_utf8mb4'RECOVERING',_utf8mb4'RECONCILING',_utf8mb4'CANCELLING',_utf8mb4'SUCCEEDED',_utf8mb4'PARTIAL',_utf8mb4'FAILED',_utf8mb4'CANCELLED')))
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='独立于目标任务的可靠回复执行';
```

## sq_runtime_binding

审计字段：完整七字段。

| 列 | 类型 | 可空 | 默认值 | 扩展 | 注释 |
|---|---|---|---|---|---|
| id | bigint | NO | NULL | auto_increment | 存储主键ID |
| binding_id | char(36) | NO | NULL |  | 绑定业务ID |
| user_id | char(36) | NO | NULL |  | 资源归属用户ID |
| task_id | char(36) | YES | NULL |  | 可选所属目标ID；普通回复为空 |
| role | varchar(16) | NO | NULL |  | MASTER/WORKER |
| agent_id | char(32) | NO | NULL |  | 固定能力版本标识 |
| framework_session_id | char(36) | NO | NULL |  | 原生会话ID |
| status | varchar(24) | NO | NULL |  | 生命周期状态 |
| native_event_cursor | bigint | NO | 0 |  | 已投影的 AgentScope 日志序号 |
| next_projection_at | datetime(3) | NO | CURRENT_TIMESTAMP(3) | DEFAULT_GENERATED | 下次读取原生日志时间 |
| creator_id | varchar(36) | NO |  |  | 创建人ID |
| creator_name | varchar(50) | NO |  |  | 创建人姓名 |
| create_time | datetime(3) | NO | CURRENT_TIMESTAMP(3) | DEFAULT_GENERATED | 创建时间 |
| last_updater_id | varchar(36) | NO |  |  | 最后更新人ID |
| last_updater_name | varchar(50) | NO |  |  | 最后更新人姓名 |
| last_update_time | datetime(3) | NO | CURRENT_TIMESTAMP(3) | DEFAULT_GENERATED on update CURRENT_TIMESTAMP(3) | 最后更新时间 |
| del_flag | char(1) | NO | 0 |  | 删除标志[0-否,1-是] |
| scope_id | char(36) | YES | NULL |  | 统一执行作用域ID |
| last_ended_turn_id | varchar(64) | YES | NULL |  | 已完成业务投影的最近原生TurnID |

索引、外键与完整建表定义（SHOW CREATE TABLE；仅结构）：

```sql
CREATE TABLE `sq_runtime_binding` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '存储主键ID',
  `binding_id` char(36) COLLATE utf8mb4_bin NOT NULL COMMENT '绑定业务ID',
  `user_id` char(36) COLLATE utf8mb4_bin NOT NULL COMMENT '资源归属用户ID',
  `task_id` char(36) COLLATE utf8mb4_bin DEFAULT NULL COMMENT '可选所属目标ID；普通回复为空',
  `role` varchar(16) COLLATE utf8mb4_bin NOT NULL COMMENT 'MASTER/WORKER',
  `agent_id` char(32) COLLATE utf8mb4_bin NOT NULL COMMENT '固定能力版本标识',
  `framework_session_id` char(36) COLLATE utf8mb4_bin NOT NULL COMMENT '原生会话ID',
  `status` varchar(24) COLLATE utf8mb4_bin NOT NULL COMMENT '生命周期状态',
  `native_event_cursor` bigint NOT NULL DEFAULT '0' COMMENT '已投影的 AgentScope 日志序号',
  `next_projection_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '下次读取原生日志时间',
  `creator_id` varchar(36) COLLATE utf8mb4_bin NOT NULL DEFAULT '' COMMENT '创建人ID',
  `creator_name` varchar(50) COLLATE utf8mb4_bin NOT NULL DEFAULT '' COMMENT '创建人姓名',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `last_updater_id` varchar(36) COLLATE utf8mb4_bin NOT NULL DEFAULT '' COMMENT '最后更新人ID',
  `last_updater_name` varchar(50) COLLATE utf8mb4_bin NOT NULL DEFAULT '' COMMENT '最后更新人姓名',
  `last_update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '最后更新时间',
  `del_flag` char(1) COLLATE utf8mb4_bin NOT NULL DEFAULT '0' COMMENT '删除标志[0-否,1-是]',
  `scope_id` char(36) COLLATE utf8mb4_bin DEFAULT NULL COMMENT '统一执行作用域ID',
  `last_ended_turn_id` varchar(64) COLLATE utf8mb4_bin DEFAULT NULL COMMENT '已完成业务投影的最近原生TurnID',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_owner_binding` (`user_id`,`binding_id`),
  KEY `fk_binding_task` (`user_id`,`task_id`),
  KEY `fk_runtime_binding_scope` (`user_id`,`scope_id`),
  CONSTRAINT `fk_binding_task` FOREIGN KEY (`user_id`, `task_id`) REFERENCES `sq_goal_task` (`user_id`, `task_id`),
  CONSTRAINT `fk_runtime_binding_scope` FOREIGN KEY (`user_id`, `scope_id`) REFERENCES `sq_execution_scope` (`user_id`, `scope_id`),
  CONSTRAINT `ck_runtime_binding_del` CHECK ((`del_flag` in (_utf8mb4'0',_utf8mb4'1')))
) ENGINE=InnoDB AUTO_INCREMENT=5 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='AgentScope会话绑定';
```

## sq_sandbox_execution

审计字段：完整七字段。

| 列 | 类型 | 可空 | 默认值 | 扩展 | 注释 |
|---|---|---|---|---|---|
| id | bigint | NO | NULL | auto_increment | 存储主键ID |
| execution_id | char(36) | NO | NULL |  | 沙箱执行业务ID |
| user_id | char(36) | NO | NULL |  | 归属用户ID |
| scope_id | char(36) | NO | NULL |  | 执行作用域ID |
| binding_id | char(36) | NO | NULL |  | 固定原生绑定ID |
| logical_key | varchar(120) | NO | NULL |  | 绑定内业务幂等键 |
| turn_id | varchar(160) | NO | NULL |  | 原生回合ID |
| tool_call_id | varchar(160) | NO | NULL |  | 原生工具调用ID |
| request_hash | char(64) | NO | NULL |  | 固定请求SHA256摘要 |
| status | varchar(20) | NO | NULL |  | 执行状态[IN_FLIGHT,SUCCEEDED,FAILED,UNKNOWN] |
| result_json | longtext | YES | NULL |  | 已知执行结果与暂存产物引用 |
| deadline_at | datetime(3) | NO | NULL |  | 失联判断截止时间 |
| creator_id | varchar(36) | NO |  |  | 创建人ID |
| creator_name | varchar(50) | NO |  |  | 创建人姓名 |
| create_time | datetime(3) | NO | CURRENT_TIMESTAMP(3) | DEFAULT_GENERATED | 创建时间 |
| last_updater_id | varchar(36) | NO |  |  | 最后更新人ID |
| last_updater_name | varchar(50) | NO |  |  | 最后更新人姓名 |
| last_update_time | datetime(3) | NO | CURRENT_TIMESTAMP(3) | DEFAULT_GENERATED on update CURRENT_TIMESTAMP(3) | 最后更新时间 |
| del_flag | char(1) | NO | 0 |  | 删除标志[0-否,1-是] |

索引、外键与完整建表定义（SHOW CREATE TABLE；仅结构）：

```sql
CREATE TABLE `sq_sandbox_execution` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '存储主键ID',
  `execution_id` char(36) COLLATE utf8mb4_bin NOT NULL COMMENT '沙箱执行业务ID',
  `user_id` char(36) COLLATE utf8mb4_bin NOT NULL COMMENT '归属用户ID',
  `scope_id` char(36) COLLATE utf8mb4_bin NOT NULL COMMENT '执行作用域ID',
  `binding_id` char(36) COLLATE utf8mb4_bin NOT NULL COMMENT '固定原生绑定ID',
  `logical_key` varchar(120) COLLATE utf8mb4_bin NOT NULL COMMENT '绑定内业务幂等键',
  `turn_id` varchar(160) COLLATE utf8mb4_bin NOT NULL COMMENT '原生回合ID',
  `tool_call_id` varchar(160) COLLATE utf8mb4_bin NOT NULL COMMENT '原生工具调用ID',
  `request_hash` char(64) COLLATE utf8mb4_bin NOT NULL COMMENT '固定请求SHA256摘要',
  `status` varchar(20) COLLATE utf8mb4_bin NOT NULL COMMENT '执行状态[IN_FLIGHT,SUCCEEDED,FAILED,UNKNOWN]',
  `result_json` longtext COLLATE utf8mb4_bin COMMENT '已知执行结果与暂存产物引用',
  `deadline_at` datetime(3) NOT NULL COMMENT '失联判断截止时间',
  `creator_id` varchar(36) COLLATE utf8mb4_bin NOT NULL DEFAULT '' COMMENT '创建人ID',
  `creator_name` varchar(50) COLLATE utf8mb4_bin NOT NULL DEFAULT '' COMMENT '创建人姓名',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `last_updater_id` varchar(36) COLLATE utf8mb4_bin NOT NULL DEFAULT '' COMMENT '最后更新人ID',
  `last_updater_name` varchar(50) COLLATE utf8mb4_bin NOT NULL DEFAULT '' COMMENT '最后更新人姓名',
  `last_update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '最后更新时间',
  `del_flag` char(1) COLLATE utf8mb4_bin NOT NULL DEFAULT '0' COMMENT '删除标志[0-否,1-是]',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sandbox_execution` (`user_id`,`execution_id`),
  UNIQUE KEY `uk_sandbox_intent` (`user_id`,`binding_id`,`logical_key`),
  UNIQUE KEY `uk_sandbox_native` (`user_id`,`binding_id`,`turn_id`,`tool_call_id`),
  KEY `fk_sandbox_scope` (`user_id`,`scope_id`),
  CONSTRAINT `fk_sandbox_binding` FOREIGN KEY (`user_id`, `binding_id`) REFERENCES `sq_runtime_binding` (`user_id`, `binding_id`),
  CONSTRAINT `fk_sandbox_scope` FOREIGN KEY (`user_id`, `scope_id`) REFERENCES `sq_execution_scope` (`user_id`, `scope_id`),
  CONSTRAINT `ck_sandbox_status` CHECK ((`status` in (_utf8mb4'IN_FLIGHT',_utf8mb4'SUCCEEDED',_utf8mb4'FAILED',_utf8mb4'UNKNOWN')))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='隔离沙箱执行账本';
```

## sq_scope_artifact

审计字段：完整七字段。

| 列 | 类型 | 可空 | 默认值 | 扩展 | 注释 |
|---|---|---|---|---|---|
| id | bigint | NO | NULL | auto_increment | 存储主键ID |
| user_id | char(36) | NO | NULL |  | 资源归属用户ID |
| scope_id | char(36) | NO | NULL |  | 所属执行作用域ID |
| artifact_id | char(36) | NO | NULL |  | 产物ID |
| creator_id | varchar(36) | NO |  |  | 创建人ID |
| creator_name | varchar(50) | NO |  |  | 创建人姓名 |
| create_time | datetime(3) | NO | CURRENT_TIMESTAMP(3) | DEFAULT_GENERATED | 创建时间 |
| last_updater_id | varchar(36) | NO |  |  | 最后更新人ID |
| last_updater_name | varchar(50) | NO |  |  | 最后更新人姓名 |
| last_update_time | datetime(3) | NO | CURRENT_TIMESTAMP(3) | DEFAULT_GENERATED on update CURRENT_TIMESTAMP(3) | 最后更新时间 |
| del_flag | char(1) | NO | 0 |  | 删除标志[0-否,1-是] |

索引、外键与完整建表定义（SHOW CREATE TABLE；仅结构）：

```sql
CREATE TABLE `sq_scope_artifact` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '存储主键ID',
  `user_id` char(36) COLLATE utf8mb4_bin NOT NULL COMMENT '资源归属用户ID',
  `scope_id` char(36) COLLATE utf8mb4_bin NOT NULL COMMENT '所属执行作用域ID',
  `artifact_id` char(36) COLLATE utf8mb4_bin NOT NULL COMMENT '产物ID',
  `creator_id` varchar(36) COLLATE utf8mb4_bin NOT NULL DEFAULT '' COMMENT '创建人ID',
  `creator_name` varchar(50) COLLATE utf8mb4_bin NOT NULL DEFAULT '' COMMENT '创建人姓名',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `last_updater_id` varchar(36) COLLATE utf8mb4_bin NOT NULL DEFAULT '' COMMENT '最后更新人ID',
  `last_updater_name` varchar(50) COLLATE utf8mb4_bin NOT NULL DEFAULT '' COMMENT '最后更新人姓名',
  `last_update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '最后更新时间',
  `del_flag` char(1) COLLATE utf8mb4_bin NOT NULL DEFAULT '0' COMMENT '删除标志[0-否,1-是]',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_scope_artifact` (`user_id`,`scope_id`,`artifact_id`),
  KEY `fk_scope_artifact_owner` (`user_id`,`artifact_id`),
  CONSTRAINT `fk_scope_artifact_owner` FOREIGN KEY (`user_id`, `artifact_id`) REFERENCES `sq_artifact` (`user_id`, `artifact_id`),
  CONSTRAINT `fk_scope_artifact_scope` FOREIGN KEY (`user_id`, `scope_id`) REFERENCES `sq_execution_scope` (`user_id`, `scope_id`),
  CONSTRAINT `ck_scope_artifact_del` CHECK ((`del_flag` in (_utf8mb4'0',_utf8mb4'1')))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='执行作用域与产物关联';
```

## sq_scope_skill_catalog

审计字段：完整七字段。

| 列 | 类型 | 可空 | 默认值 | 扩展 | 注释 |
|---|---|---|---|---|---|
| id | bigint | NO | NULL | auto_increment | 存储主键ID |
| catalog_id | char(36) | NO | NULL |  | 固定目录业务ID |
| user_id | char(36) | NO | NULL |  | 资源所属用户ID |
| scope_id | char(36) | NO | NULL |  | 固定目录执行作用域ID |
| creator_id | varchar(36) | NO |  |  | 创建人ID |
| creator_name | varchar(50) | NO |  |  | 创建人姓名 |
| create_time | datetime(3) | NO | CURRENT_TIMESTAMP(3) | DEFAULT_GENERATED | 创建时间 |
| last_updater_id | varchar(36) | NO |  |  | 最后更新人ID |
| last_updater_name | varchar(50) | NO |  |  | 最后更新人姓名 |
| last_update_time | datetime(3) | NO | CURRENT_TIMESTAMP(3) | DEFAULT_GENERATED on update CURRENT_TIMESTAMP(3) | 最后更新时间 |
| del_flag | char(1) | NO | 0 |  | 删除标志[0-否,1-是] |

索引、外键与完整建表定义（SHOW CREATE TABLE；仅结构）：

```sql
CREATE TABLE `sq_scope_skill_catalog` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '存储主键ID',
  `catalog_id` char(36) COLLATE utf8mb4_bin NOT NULL COMMENT '固定目录业务ID',
  `user_id` char(36) COLLATE utf8mb4_bin NOT NULL COMMENT '资源所属用户ID',
  `scope_id` char(36) COLLATE utf8mb4_bin NOT NULL COMMENT '固定目录执行作用域ID',
  `creator_id` varchar(36) COLLATE utf8mb4_bin NOT NULL DEFAULT '' COMMENT '创建人ID',
  `creator_name` varchar(50) COLLATE utf8mb4_bin NOT NULL DEFAULT '' COMMENT '创建人姓名',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `last_updater_id` varchar(36) COLLATE utf8mb4_bin NOT NULL DEFAULT '' COMMENT '最后更新人ID',
  `last_updater_name` varchar(50) COLLATE utf8mb4_bin NOT NULL DEFAULT '' COMMENT '最后更新人姓名',
  `last_update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '最后更新时间',
  `del_flag` char(1) COLLATE utf8mb4_bin NOT NULL DEFAULT '0' COMMENT '删除标志[0-否,1-是]',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_scope_skill_catalog_id` (`user_id`,`catalog_id`),
  UNIQUE KEY `uk_scope_skill_catalog` (`user_id`,`scope_id`),
  CONSTRAINT `fk_skill_catalog_scope` FOREIGN KEY (`user_id`, `scope_id`) REFERENCES `sq_execution_scope` (`user_id`, `scope_id`),
  CONSTRAINT `ck_scope_skill_catalog_del` CHECK ((`del_flag` in (_utf8mb4'0',_utf8mb4'1')))
) ENGINE=InnoDB AUTO_INCREMENT=3 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='执行作用域固定技能目录';
```

## sq_scope_skill_ref

审计字段：完整七字段。

| 列 | 类型 | 可空 | 默认值 | 扩展 | 注释 |
|---|---|---|---|---|---|
| id | bigint | NO | NULL | auto_increment | 存储主键ID |
| reference_id | char(36) | NO | NULL |  | 技能引用业务ID |
| user_id | char(36) | NO | NULL |  | 资源所属用户ID |
| scope_id | char(36) | NO | NULL |  | 固定引用执行作用域ID |
| skill_version_id | char(36) | NO | NULL |  | 固定技能版本ID |
| content_hash | char(64) | NO | NULL |  | 固定技能内容摘要 |
| authorization_revision | bigint | NO | NULL |  | 固定时的安全授权代际 |
| creator_id | varchar(36) | NO |  |  | 创建人ID |
| creator_name | varchar(50) | NO |  |  | 创建人姓名 |
| create_time | datetime(3) | NO | CURRENT_TIMESTAMP(3) | DEFAULT_GENERATED | 创建时间 |
| last_updater_id | varchar(36) | NO |  |  | 最后更新人ID |
| last_updater_name | varchar(50) | NO |  |  | 最后更新人姓名 |
| last_update_time | datetime(3) | NO | CURRENT_TIMESTAMP(3) | DEFAULT_GENERATED on update CURRENT_TIMESTAMP(3) | 最后更新时间 |
| del_flag | char(1) | NO | 0 |  | 删除标志[0-否,1-是] |

索引、外键与完整建表定义（SHOW CREATE TABLE；仅结构）：

```sql
CREATE TABLE `sq_scope_skill_ref` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '存储主键ID',
  `reference_id` char(36) COLLATE utf8mb4_bin NOT NULL COMMENT '技能引用业务ID',
  `user_id` char(36) COLLATE utf8mb4_bin NOT NULL COMMENT '资源所属用户ID',
  `scope_id` char(36) COLLATE utf8mb4_bin NOT NULL COMMENT '固定引用执行作用域ID',
  `skill_version_id` char(36) COLLATE utf8mb4_bin NOT NULL COMMENT '固定技能版本ID',
  `content_hash` char(64) COLLATE utf8mb4_bin NOT NULL COMMENT '固定技能内容摘要',
  `authorization_revision` bigint NOT NULL COMMENT '固定时的安全授权代际',
  `creator_id` varchar(36) COLLATE utf8mb4_bin NOT NULL DEFAULT '' COMMENT '创建人ID',
  `creator_name` varchar(50) COLLATE utf8mb4_bin NOT NULL DEFAULT '' COMMENT '创建人姓名',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `last_updater_id` varchar(36) COLLATE utf8mb4_bin NOT NULL DEFAULT '' COMMENT '最后更新人ID',
  `last_updater_name` varchar(50) COLLATE utf8mb4_bin NOT NULL DEFAULT '' COMMENT '最后更新人姓名',
  `last_update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '最后更新时间',
  `del_flag` char(1) COLLATE utf8mb4_bin NOT NULL DEFAULT '0' COMMENT '删除标志[0-否,1-是]',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_scope_skill_ref_id` (`user_id`,`reference_id`),
  UNIQUE KEY `uk_scope_skill_ref` (`user_id`,`scope_id`,`skill_version_id`),
  KEY `fk_scope_skill_ref_version` (`user_id`,`skill_version_id`),
  CONSTRAINT `fk_scope_skill_ref_catalog` FOREIGN KEY (`user_id`, `scope_id`) REFERENCES `sq_scope_skill_catalog` (`user_id`, `scope_id`),
  CONSTRAINT `fk_scope_skill_ref_version` FOREIGN KEY (`user_id`, `skill_version_id`) REFERENCES `sq_skill_version` (`user_id`, `skill_version_id`),
  CONSTRAINT `ck_scope_skill_ref_del` CHECK ((`del_flag` in (_utf8mb4'0',_utf8mb4'1')))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='执行作用域固定技能版本引用';
```

## sq_secret_vault

审计字段：完整七字段。

| 列 | 类型 | 可空 | 默认值 | 扩展 | 注释 |
|---|---|---|---|---|---|
| id | bigint | NO | NULL | auto_increment | 存储主键ID |
| secret_ref | varchar(240) | NO | NULL |  | 密钥引用；格式 local-vault:keyId:uuid |
| key_id | varchar(64) | NO | NULL |  | 加密密钥版本ID；轮换期间新旧并存 |
| cipher_bytes | varbinary(2048) | NO | NULL |  | AES-GCM 密文；IV 前置 |
| creator_id | varchar(36) | NO |  |  | 创建人ID |
| creator_name | varchar(50) | NO |  |  | 创建人姓名 |
| create_time | datetime(3) | NO | CURRENT_TIMESTAMP(3) | DEFAULT_GENERATED | 创建时间 |
| last_updater_id | varchar(36) | NO |  |  | 最后更新人ID |
| last_updater_name | varchar(50) | NO |  |  | 最后更新人姓名 |
| last_update_time | datetime(3) | NO | CURRENT_TIMESTAMP(3) | DEFAULT_GENERATED on update CURRENT_TIMESTAMP(3) | 最后更新时间 |
| del_flag | char(1) | NO | 0 |  | 删除标志[0-否,1-是] |

索引、外键与完整建表定义（SHOW CREATE TABLE；仅结构）：

```sql
CREATE TABLE `sq_secret_vault` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '存储主键ID',
  `secret_ref` varchar(240) COLLATE utf8mb4_bin NOT NULL COMMENT '密钥引用；格式 local-vault:keyId:uuid',
  `key_id` varchar(64) COLLATE utf8mb4_bin NOT NULL COMMENT '加密密钥版本ID；轮换期间新旧并存',
  `cipher_bytes` varbinary(2048) NOT NULL COMMENT 'AES-GCM 密文；IV 前置',
  `creator_id` varchar(36) COLLATE utf8mb4_bin NOT NULL DEFAULT '' COMMENT '创建人ID',
  `creator_name` varchar(50) COLLATE utf8mb4_bin NOT NULL DEFAULT '' COMMENT '创建人姓名',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `last_updater_id` varchar(36) COLLATE utf8mb4_bin NOT NULL DEFAULT '' COMMENT '最后更新人ID',
  `last_updater_name` varchar(50) COLLATE utf8mb4_bin NOT NULL DEFAULT '' COMMENT '最后更新人姓名',
  `last_update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '最后更新时间',
  `del_flag` char(1) COLLATE utf8mb4_bin NOT NULL DEFAULT '0' COMMENT '删除标志[0-否,1-是]',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_secret_ref` (`secret_ref`),
  CONSTRAINT `ck_secret_vault_del` CHECK ((`del_flag` in (_utf8mb4'0',_utf8mb4'1')))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='本地密钥保险库密文；不保存明文';
```

## sq_skill_file

审计字段：完整七字段。

| 列 | 类型 | 可空 | 默认值 | 扩展 | 注释 |
|---|---|---|---|---|---|
| id | bigint | NO | NULL | auto_increment | 存储主键ID |
| skill_file_id | char(36) | NO | NULL |  | 技能文件业务ID |
| user_id | char(36) | NO | NULL |  | 所属用户ID |
| skill_version_id | char(36) | NO | NULL |  | 所属技能版本ID |
| file_path | varchar(240) | NO | NULL |  | 包内规范化相对路径 |
| file_content | mediumtext | NO | NULL |  | UTF8文本正文 |
| creator_id | varchar(36) | NO |  |  | 创建人ID |
| creator_name | varchar(50) | NO |  |  | 创建人姓名 |
| create_time | datetime(3) | NO | CURRENT_TIMESTAMP(3) | DEFAULT_GENERATED | 创建时间 |
| last_updater_id | varchar(36) | NO |  |  | 最后更新人ID |
| last_updater_name | varchar(50) | NO |  |  | 最后更新人姓名 |
| last_update_time | datetime(3) | NO | CURRENT_TIMESTAMP(3) | DEFAULT_GENERATED on update CURRENT_TIMESTAMP(3) | 最后更新时间 |
| del_flag | char(1) | NO | 0 |  | 删除标志[0-否,1-是] |

索引、外键与完整建表定义（SHOW CREATE TABLE；仅结构）：

```sql
CREATE TABLE `sq_skill_file` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '存储主键ID',
  `skill_file_id` char(36) COLLATE utf8mb4_bin NOT NULL COMMENT '技能文件业务ID',
  `user_id` char(36) COLLATE utf8mb4_bin NOT NULL COMMENT '所属用户ID',
  `skill_version_id` char(36) COLLATE utf8mb4_bin NOT NULL COMMENT '所属技能版本ID',
  `file_path` varchar(240) COLLATE utf8mb4_bin NOT NULL COMMENT '包内规范化相对路径',
  `file_content` mediumtext COLLATE utf8mb4_bin NOT NULL COMMENT 'UTF8文本正文',
  `creator_id` varchar(36) COLLATE utf8mb4_bin NOT NULL DEFAULT '' COMMENT '创建人ID',
  `creator_name` varchar(50) COLLATE utf8mb4_bin NOT NULL DEFAULT '' COMMENT '创建人姓名',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `last_updater_id` varchar(36) COLLATE utf8mb4_bin NOT NULL DEFAULT '' COMMENT '最后更新人ID',
  `last_updater_name` varchar(50) COLLATE utf8mb4_bin NOT NULL DEFAULT '' COMMENT '最后更新人姓名',
  `last_update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '最后更新时间',
  `del_flag` char(1) COLLATE utf8mb4_bin NOT NULL DEFAULT '0' COMMENT '删除标志[0-否,1-是]',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_skill_file_id` (`user_id`,`skill_file_id`),
  UNIQUE KEY `uk_skill_file_path` (`user_id`,`skill_version_id`,`file_path`),
  CONSTRAINT `fk_skill_file_version` FOREIGN KEY (`user_id`, `skill_version_id`) REFERENCES `sq_skill_version` (`user_id`, `skill_version_id`),
  CONSTRAINT `ck_skill_file_del` CHECK ((`del_flag` in (_utf8mb4'0',_utf8mb4'1')))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='技能版本只读文本文件';
```

## sq_skill_grant

审计字段：完整七字段。

| 列 | 类型 | 可空 | 默认值 | 扩展 | 注释 |
|---|---|---|---|---|---|
| id | bigint | NO | NULL | auto_increment | 存储主键ID |
| grant_id | char(36) | NO | NULL |  | 技能授权业务ID |
| user_id | char(36) | NO | NULL |  | 授权用户ID |
| skill_version_id | char(36) | NO | NULL |  | 被授权技能版本ID |
| enabled | tinyint(1) | NO | 0 |  | 是否允许新执行选择 |
| version | bigint | NO | 0 |  | 授权乐观版本 |
| creator_id | varchar(36) | NO |  |  | 创建人ID |
| creator_name | varchar(50) | NO |  |  | 创建人姓名 |
| create_time | datetime(3) | NO | CURRENT_TIMESTAMP(3) | DEFAULT_GENERATED | 创建时间 |
| last_updater_id | varchar(36) | NO |  |  | 最后更新人ID |
| last_updater_name | varchar(50) | NO |  |  | 最后更新人姓名 |
| last_update_time | datetime(3) | NO | CURRENT_TIMESTAMP(3) | DEFAULT_GENERATED on update CURRENT_TIMESTAMP(3) | 最后更新时间 |
| del_flag | char(1) | NO | 0 |  | 删除标志[0-否,1-是] |

索引、外键与完整建表定义（SHOW CREATE TABLE；仅结构）：

```sql
CREATE TABLE `sq_skill_grant` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '存储主键ID',
  `grant_id` char(36) COLLATE utf8mb4_bin NOT NULL COMMENT '技能授权业务ID',
  `user_id` char(36) COLLATE utf8mb4_bin NOT NULL COMMENT '授权用户ID',
  `skill_version_id` char(36) COLLATE utf8mb4_bin NOT NULL COMMENT '被授权技能版本ID',
  `enabled` tinyint(1) NOT NULL DEFAULT '0' COMMENT '是否允许新执行选择',
  `version` bigint NOT NULL DEFAULT '0' COMMENT '授权乐观版本',
  `creator_id` varchar(36) COLLATE utf8mb4_bin NOT NULL DEFAULT '' COMMENT '创建人ID',
  `creator_name` varchar(50) COLLATE utf8mb4_bin NOT NULL DEFAULT '' COMMENT '创建人姓名',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `last_updater_id` varchar(36) COLLATE utf8mb4_bin NOT NULL DEFAULT '' COMMENT '最后更新人ID',
  `last_updater_name` varchar(50) COLLATE utf8mb4_bin NOT NULL DEFAULT '' COMMENT '最后更新人姓名',
  `last_update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '最后更新时间',
  `del_flag` char(1) COLLATE utf8mb4_bin NOT NULL DEFAULT '0' COMMENT '删除标志[0-否,1-是]',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_skill_grant_id` (`user_id`,`grant_id`),
  UNIQUE KEY `uk_skill_grant_version` (`user_id`,`skill_version_id`),
  CONSTRAINT `fk_skill_grant_version` FOREIGN KEY (`user_id`, `skill_version_id`) REFERENCES `sq_skill_version` (`user_id`, `skill_version_id`),
  CONSTRAINT `ck_skill_grant_del` CHECK ((`del_flag` in (_utf8mb4'0',_utf8mb4'1')))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='用户技能版本使用授权';
```

## sq_skill_version

审计字段：完整七字段。

| 列 | 类型 | 可空 | 默认值 | 扩展 | 注释 |
|---|---|---|---|---|---|
| id | bigint | NO | NULL | auto_increment | 存储主键ID |
| skill_version_id | char(36) | NO | NULL |  | 技能版本业务ID |
| user_id | char(36) | NO | NULL |  | 所属用户ID |
| skill_name | varchar(100) | NO | NULL |  | 用户内技能名称 |
| skill_version | varchar(80) | NO | NULL |  | 技能内容版本 |
| description | varchar(4000) | NO | NULL |  | 技能目录描述 |
| content_hash | char(64) | NO | NULL |  | 规范化文本包SHA256摘要 |
| required_tools_json | json | NO | NULL |  | 使用技能所需工具名称列表 |
| status | varchar(20) | NO | ENABLED |  | 版本状态[ENABLED,REVOKED] |
| authorization_revision | bigint | NO | 0 |  | 安全授权代际 |
| creator_id | varchar(36) | NO |  |  | 创建人ID |
| creator_name | varchar(50) | NO |  |  | 创建人姓名 |
| create_time | datetime(3) | NO | CURRENT_TIMESTAMP(3) | DEFAULT_GENERATED | 创建时间 |
| last_updater_id | varchar(36) | NO |  |  | 最后更新人ID |
| last_updater_name | varchar(50) | NO |  |  | 最后更新人姓名 |
| last_update_time | datetime(3) | NO | CURRENT_TIMESTAMP(3) | DEFAULT_GENERATED on update CURRENT_TIMESTAMP(3) | 最后更新时间 |
| del_flag | char(1) | NO | 0 |  | 删除标志[0-否,1-是] |

索引、外键与完整建表定义（SHOW CREATE TABLE；仅结构）：

```sql
CREATE TABLE `sq_skill_version` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '存储主键ID',
  `skill_version_id` char(36) COLLATE utf8mb4_bin NOT NULL COMMENT '技能版本业务ID',
  `user_id` char(36) COLLATE utf8mb4_bin NOT NULL COMMENT '所属用户ID',
  `skill_name` varchar(100) COLLATE utf8mb4_bin NOT NULL COMMENT '用户内技能名称',
  `skill_version` varchar(80) COLLATE utf8mb4_bin NOT NULL COMMENT '技能内容版本',
  `description` varchar(4000) COLLATE utf8mb4_bin NOT NULL COMMENT '技能目录描述',
  `content_hash` char(64) COLLATE utf8mb4_bin NOT NULL COMMENT '规范化文本包SHA256摘要',
  `required_tools_json` json NOT NULL COMMENT '使用技能所需工具名称列表',
  `status` varchar(20) COLLATE utf8mb4_bin NOT NULL DEFAULT 'ENABLED' COMMENT '版本状态[ENABLED,REVOKED]',
  `authorization_revision` bigint NOT NULL DEFAULT '0' COMMENT '安全授权代际',
  `creator_id` varchar(36) COLLATE utf8mb4_bin NOT NULL DEFAULT '' COMMENT '创建人ID',
  `creator_name` varchar(50) COLLATE utf8mb4_bin NOT NULL DEFAULT '' COMMENT '创建人姓名',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `last_updater_id` varchar(36) COLLATE utf8mb4_bin NOT NULL DEFAULT '' COMMENT '最后更新人ID',
  `last_updater_name` varchar(50) COLLATE utf8mb4_bin NOT NULL DEFAULT '' COMMENT '最后更新人姓名',
  `last_update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '最后更新时间',
  `del_flag` char(1) COLLATE utf8mb4_bin NOT NULL DEFAULT '0' COMMENT '删除标志[0-否,1-是]',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_skill_version` (`user_id`,`skill_version_id`),
  UNIQUE KEY `uk_skill_name_version` (`user_id`,`skill_name`,`skill_version`),
  CONSTRAINT `ck_skill_version_del` CHECK ((`del_flag` in (_utf8mb4'0',_utf8mb4'1'))),
  CONSTRAINT `ck_skill_version_status` CHECK ((`status` in (_utf8mb4'ENABLED',_utf8mb4'REVOKED')))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='用户技能不可变版本';
```

## sq_task_artifact

审计字段：完整七字段。

| 列 | 类型 | 可空 | 默认值 | 扩展 | 注释 |
|---|---|---|---|---|---|
| id | bigint | NO | NULL | auto_increment | 存储主键ID |
| user_id | char(36) | NO | NULL |  | 归属用户ID |
| task_id | char(36) | NO | NULL |  | 关联目标ID |
| artifact_id | char(36) | NO | NULL |  | 关联产物ID |
| creator_id | varchar(36) | NO |  |  | 创建人ID |
| creator_name | varchar(50) | NO |  |  | 创建人姓名 |
| create_time | datetime(3) | NO | CURRENT_TIMESTAMP(3) | DEFAULT_GENERATED | 创建时间 |
| last_updater_id | varchar(36) | NO |  |  | 最后更新人ID |
| last_updater_name | varchar(50) | NO |  |  | 最后更新人姓名 |
| last_update_time | datetime(3) | NO | CURRENT_TIMESTAMP(3) | DEFAULT_GENERATED on update CURRENT_TIMESTAMP(3) | 最后更新时间 |
| del_flag | char(1) | NO | 0 |  | 删除标志[0-否,1-是] |

索引、外键与完整建表定义（SHOW CREATE TABLE；仅结构）：

```sql
CREATE TABLE `sq_task_artifact` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '存储主键ID',
  `user_id` char(36) COLLATE utf8mb4_bin NOT NULL COMMENT '归属用户ID',
  `task_id` char(36) COLLATE utf8mb4_bin NOT NULL COMMENT '关联目标ID',
  `artifact_id` char(36) COLLATE utf8mb4_bin NOT NULL COMMENT '关联产物ID',
  `creator_id` varchar(36) COLLATE utf8mb4_bin NOT NULL DEFAULT '' COMMENT '创建人ID',
  `creator_name` varchar(50) COLLATE utf8mb4_bin NOT NULL DEFAULT '' COMMENT '创建人姓名',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `last_updater_id` varchar(36) COLLATE utf8mb4_bin NOT NULL DEFAULT '' COMMENT '最后更新人ID',
  `last_updater_name` varchar(50) COLLATE utf8mb4_bin NOT NULL DEFAULT '' COMMENT '最后更新人姓名',
  `last_update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '最后更新时间',
  `del_flag` char(1) COLLATE utf8mb4_bin NOT NULL DEFAULT '0' COMMENT '删除标志[0-否,1-是]',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_task_artifact` (`task_id`,`artifact_id`),
  KEY `fk_task_artifact_task` (`user_id`,`task_id`),
  KEY `fk_task_artifact_owner` (`user_id`,`artifact_id`),
  CONSTRAINT `fk_task_artifact_owner` FOREIGN KEY (`user_id`, `artifact_id`) REFERENCES `sq_artifact` (`user_id`, `artifact_id`),
  CONSTRAINT `fk_task_artifact_task` FOREIGN KEY (`user_id`, `task_id`) REFERENCES `sq_goal_task` (`user_id`, `task_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='目标与产物归属关联';
```

## sq_task_directive

审计字段：完整七字段。

| 列 | 类型 | 可空 | 默认值 | 扩展 | 注释 |
|---|---|---|---|---|---|
| id | bigint | NO | NULL | auto_increment | 存储主键ID |
| directive_id | char(36) | NO | NULL |  | 目标修订指令业务ID |
| user_id | char(36) | NO | NULL |  | 归属用户ID |
| conversation_id | char(36) | NO | NULL |  | 归属会话ID |
| conversation_generation | bigint | NO | NULL |  | 受理时会话代际 |
| source_message_id | char(36) | NO | NULL |  | 受权用户消息ID |
| source_scope_id | char(36) | NO | NULL |  | 来源回复执行作用域ID |
| task_id | char(36) | NO | NULL |  | 当前受权目标ID |
| logical_key | varchar(160) | NO | NULL |  | 来源Scope内稳定业务调用键 |
| payload_hash | char(64) | NO | NULL |  | 规范化受理参数摘要 |
| expected_version | bigint | NO | NULL |  | 受理时校验的目标运行版本 |
| expected_goal_revision | bigint | NO | NULL |  | 用户修改所基于的目标语义修订号 |
| kind | varchar(20) | NO | NULL |  | 类型[SUPPLEMENT,REPLACE] |
| content | longtext | NO | NULL |  | Master归纳的修改建议，不冒充用户原话 |
| source_text | longtext | NO | NULL |  | 受权用户消息的实际原文 |
| artifact_ids_json | json | NO | NULL |  | 来源消息的受权附件引用 |
| status | varchar(20) | NO | NULL |  | 状态[ACCEPTED,APPLIED,REJECTED] |
| command_id | char(36) | YES | NULL |  | 独立任务协调命令ID |
| applied_version | bigint | YES | NULL |  | 实际应用后的目标运行版本 |
| applied_goal_revision | bigint | YES | NULL |  | 实际应用后的目标语义修订号 |
| reason_code | varchar(64) | YES | NULL |  | 未应用的结构化原因码 |
| reason_message | varchar(500) | YES | NULL |  | 可向用户解释的安全原因 |
| next_attempt_at | datetime(3) | NO | CURRENT_TIMESTAMP(3) | DEFAULT_GENERATED | 下次协调检查时间 |
| creator_id | varchar(36) | NO |  |  | 创建人ID |
| creator_name | varchar(50) | NO |  |  | 创建人姓名 |
| create_time | datetime(3) | NO | CURRENT_TIMESTAMP(3) | DEFAULT_GENERATED | 创建时间 |
| last_updater_id | varchar(36) | NO |  |  | 最后更新人ID |
| last_updater_name | varchar(50) | NO |  |  | 最后更新人姓名 |
| last_update_time | datetime(3) | NO | CURRENT_TIMESTAMP(3) | DEFAULT_GENERATED on update CURRENT_TIMESTAMP(3) | 最后更新时间 |
| del_flag | char(1) | NO | 0 |  | 删除标志[0-否,1-是] |

索引、外键与完整建表定义（SHOW CREATE TABLE；仅结构）：

```sql
CREATE TABLE `sq_task_directive` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '存储主键ID',
  `directive_id` char(36) COLLATE utf8mb4_bin NOT NULL COMMENT '目标修订指令业务ID',
  `user_id` char(36) COLLATE utf8mb4_bin NOT NULL COMMENT '归属用户ID',
  `conversation_id` char(36) COLLATE utf8mb4_bin NOT NULL COMMENT '归属会话ID',
  `conversation_generation` bigint NOT NULL COMMENT '受理时会话代际',
  `source_message_id` char(36) COLLATE utf8mb4_bin NOT NULL COMMENT '受权用户消息ID',
  `source_scope_id` char(36) COLLATE utf8mb4_bin NOT NULL COMMENT '来源回复执行作用域ID',
  `task_id` char(36) COLLATE utf8mb4_bin NOT NULL COMMENT '当前受权目标ID',
  `logical_key` varchar(160) COLLATE utf8mb4_bin NOT NULL COMMENT '来源Scope内稳定业务调用键',
  `payload_hash` char(64) COLLATE utf8mb4_bin NOT NULL COMMENT '规范化受理参数摘要',
  `expected_version` bigint NOT NULL COMMENT '受理时校验的目标运行版本',
  `expected_goal_revision` bigint NOT NULL COMMENT '用户修改所基于的目标语义修订号',
  `kind` varchar(20) COLLATE utf8mb4_bin NOT NULL COMMENT '类型[SUPPLEMENT,REPLACE]',
  `content` longtext COLLATE utf8mb4_bin NOT NULL COMMENT 'Master归纳的修改建议，不冒充用户原话',
  `source_text` longtext COLLATE utf8mb4_bin NOT NULL COMMENT '受权用户消息的实际原文',
  `artifact_ids_json` json NOT NULL COMMENT '来源消息的受权附件引用',
  `status` varchar(20) COLLATE utf8mb4_bin NOT NULL COMMENT '状态[ACCEPTED,APPLIED,REJECTED]',
  `command_id` char(36) COLLATE utf8mb4_bin DEFAULT NULL COMMENT '独立任务协调命令ID',
  `applied_version` bigint DEFAULT NULL COMMENT '实际应用后的目标运行版本',
  `applied_goal_revision` bigint DEFAULT NULL COMMENT '实际应用后的目标语义修订号',
  `reason_code` varchar(64) COLLATE utf8mb4_bin DEFAULT NULL COMMENT '未应用的结构化原因码',
  `reason_message` varchar(500) COLLATE utf8mb4_bin DEFAULT NULL COMMENT '可向用户解释的安全原因',
  `next_attempt_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '下次协调检查时间',
  `creator_id` varchar(36) COLLATE utf8mb4_bin NOT NULL DEFAULT '' COMMENT '创建人ID',
  `creator_name` varchar(50) COLLATE utf8mb4_bin NOT NULL DEFAULT '' COMMENT '创建人姓名',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `last_updater_id` varchar(36) COLLATE utf8mb4_bin NOT NULL DEFAULT '' COMMENT '最后更新人ID',
  `last_updater_name` varchar(50) COLLATE utf8mb4_bin NOT NULL DEFAULT '' COMMENT '最后更新人姓名',
  `last_update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '最后更新时间',
  `del_flag` char(1) COLLATE utf8mb4_bin NOT NULL DEFAULT '0' COMMENT '删除标志[0-否,1-是]',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_task_directive` (`user_id`,`directive_id`),
  UNIQUE KEY `uk_directive_source_key` (`user_id`,`source_scope_id`,`logical_key`),
  KEY `idx_directive_due` (`status`,`next_attempt_at`,`id`),
  KEY `idx_directive_task` (`user_id`,`task_id`,`id`),
  KEY `fk_directive_conversation` (`user_id`,`conversation_id`),
  KEY `fk_directive_source` (`user_id`,`source_message_id`),
  CONSTRAINT `fk_directive_conversation` FOREIGN KEY (`user_id`, `conversation_id`) REFERENCES `sq_conversation` (`user_id`, `conversation_id`),
  CONSTRAINT `fk_directive_scope` FOREIGN KEY (`user_id`, `source_scope_id`) REFERENCES `sq_execution_scope` (`user_id`, `scope_id`),
  CONSTRAINT `fk_directive_source` FOREIGN KEY (`user_id`, `source_message_id`) REFERENCES `sq_message` (`user_id`, `message_id`),
  CONSTRAINT `fk_directive_task` FOREIGN KEY (`user_id`, `task_id`) REFERENCES `sq_goal_task` (`user_id`, `task_id`),
  CONSTRAINT `ck_directive_del` CHECK ((`del_flag` in (_utf8mb4'0',_utf8mb4'1'))),
  CONSTRAINT `ck_directive_kind` CHECK ((`kind` in (_utf8mb4'SUPPLEMENT',_utf8mb4'REPLACE'))),
  CONSTRAINT `ck_directive_status` CHECK ((`status` in (_utf8mb4'ACCEPTED',_utf8mb4'APPLIED',_utf8mb4'REJECTED'))),
  CONSTRAINT `ck_directive_versions` CHECK (((`expected_version` >= 0) and (`expected_goal_revision` >= 0)))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='用户目标修改的持久受理与应用事实';
```

## sq_task_event

审计字段：完整七字段。

| 列 | 类型 | 可空 | 默认值 | 扩展 | 注释 |
|---|---|---|---|---|---|
| id | bigint | NO | NULL | auto_increment | 存储主键ID |
| user_id | char(36) | NO | NULL |  | 资源归属用户ID |
| task_id | char(36) | NO | NULL |  | 目标ID |
| event_seq | bigint | NO | NULL |  | 目标内事件序号 |
| producer_key | varchar(200) | NO | NULL |  | 稳定生产事件键 |
| event_type | varchar(64) | NO | NULL |  | 公开事件类型 |
| payload_json | json | NO | NULL |  | 公开事件载荷 |
| occurred_at | datetime(3) | NO | NULL |  | 发生时间 |
| creator_id | varchar(36) | NO |  |  | 创建人ID |
| creator_name | varchar(50) | NO |  |  | 创建人姓名 |
| create_time | datetime(3) | NO | CURRENT_TIMESTAMP(3) | DEFAULT_GENERATED | 创建时间 |
| last_updater_id | varchar(36) | NO |  |  | 最后更新人ID |
| last_updater_name | varchar(50) | NO |  |  | 最后更新人姓名 |
| last_update_time | datetime(3) | NO | CURRENT_TIMESTAMP(3) | DEFAULT_GENERATED on update CURRENT_TIMESTAMP(3) | 最后更新时间 |
| del_flag | char(1) | NO | 0 |  | 删除标志[0-否,1-是] |

索引、外键与完整建表定义（SHOW CREATE TABLE；仅结构）：

```sql
CREATE TABLE `sq_task_event` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '存储主键ID',
  `user_id` char(36) COLLATE utf8mb4_bin NOT NULL COMMENT '资源归属用户ID',
  `task_id` char(36) COLLATE utf8mb4_bin NOT NULL COMMENT '目标ID',
  `event_seq` bigint NOT NULL COMMENT '目标内事件序号',
  `producer_key` varchar(200) COLLATE utf8mb4_bin NOT NULL COMMENT '稳定生产事件键',
  `event_type` varchar(64) COLLATE utf8mb4_bin NOT NULL COMMENT '公开事件类型',
  `payload_json` json NOT NULL COMMENT '公开事件载荷',
  `occurred_at` datetime(3) NOT NULL COMMENT '发生时间',
  `creator_id` varchar(36) COLLATE utf8mb4_bin NOT NULL DEFAULT '' COMMENT '创建人ID',
  `creator_name` varchar(50) COLLATE utf8mb4_bin NOT NULL DEFAULT '' COMMENT '创建人姓名',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `last_updater_id` varchar(36) COLLATE utf8mb4_bin NOT NULL DEFAULT '' COMMENT '最后更新人ID',
  `last_updater_name` varchar(50) COLLATE utf8mb4_bin NOT NULL DEFAULT '' COMMENT '最后更新人姓名',
  `last_update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '最后更新时间',
  `del_flag` char(1) COLLATE utf8mb4_bin NOT NULL DEFAULT '0' COMMENT '删除标志[0-否,1-是]',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_task_seq` (`user_id`,`task_id`,`event_seq`),
  UNIQUE KEY `uk_producer` (`user_id`,`task_id`,`producer_key`),
  CONSTRAINT `fk_event_task` FOREIGN KEY (`user_id`, `task_id`) REFERENCES `sq_goal_task` (`user_id`, `task_id`),
  CONSTRAINT `ck_task_event_del` CHECK ((`del_flag` in (_utf8mb4'0',_utf8mb4'1')))
) ENGINE=InnoDB AUTO_INCREMENT=13 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='用户可见持久事件';
```

## sq_user_budget

审计字段：完整七字段。

| 列 | 类型 | 可空 | 默认值 | 扩展 | 注释 |
|---|---|---|---|---|---|
| id | bigint | NO | NULL | auto_increment | 存储主键ID |
| user_id | char(36) | NO | NULL |  | 资源归属用户ID |
| creator_id | varchar(36) | NO |  |  | 创建人ID |
| creator_name | varchar(50) | NO |  |  | 创建人姓名 |
| create_time | datetime(3) | NO | CURRENT_TIMESTAMP(3) | DEFAULT_GENERATED | 创建时间 |
| last_updater_id | varchar(36) | NO |  |  | 最后更新人ID |
| last_updater_name | varchar(50) | NO |  |  | 最后更新人姓名 |
| last_update_time | datetime(3) | NO | CURRENT_TIMESTAMP(3) | DEFAULT_GENERATED on update CURRENT_TIMESTAMP(3) | 最后更新时间 |
| del_flag | char(1) | NO | 0 |  | 删除标志[0-否,1-是] |

索引、外键与完整建表定义（SHOW CREATE TABLE；仅结构）：

```sql
CREATE TABLE `sq_user_budget` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '存储主键ID',
  `user_id` char(36) COLLATE utf8mb4_bin NOT NULL COMMENT '资源归属用户ID',
  `creator_id` varchar(36) COLLATE utf8mb4_bin NOT NULL DEFAULT '' COMMENT '创建人ID',
  `creator_name` varchar(50) COLLATE utf8mb4_bin NOT NULL DEFAULT '' COMMENT '创建人姓名',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `last_updater_id` varchar(36) COLLATE utf8mb4_bin NOT NULL DEFAULT '' COMMENT '最后更新人ID',
  `last_updater_name` varchar(50) COLLATE utf8mb4_bin NOT NULL DEFAULT '' COMMENT '最后更新人姓名',
  `last_update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '最后更新时间',
  `del_flag` char(1) COLLATE utf8mb4_bin NOT NULL DEFAULT '0' COMMENT '删除标志[0-否,1-是]',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_budget` (`user_id`),
  CONSTRAINT `ck_user_budget_del` CHECK ((`del_flag` in (_utf8mb4'0',_utf8mb4'1')))
) ENGINE=InnoDB AUTO_INCREMENT=30 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='用户全局预算预留串行锁';
```

## sq_worker_definition

审计字段：完整七字段。

| 列 | 类型 | 可空 | 默认值 | 扩展 | 注释 |
|---|---|---|---|---|---|
| id | bigint | NO | NULL | auto_increment | 存储主键ID |
| definition_id | char(36) | NO | NULL |  | 能力定义业务ID |
| worker_key | varchar(80) | NO | NULL |  | 稳定能力键 |
| worker_version | varchar(40) | NO | NULL |  | 不可变能力版本 |
| status | varchar(16) | NO | NULL |  | 状态[ENABLED,DISABLED,REVOKED] |
| version | bigint | NO | 0 |  | 状态乐观版本 |
| definition_json | json | NO | NULL |  | 不可变能力定义 |
| publish_key | varchar(160) | NO | NULL |  | 发布幂等键 |
| payload_hash | char(64) | NO | NULL |  | 发布内容SHA256摘要 |
| last_status_key | varchar(160) | YES | NULL |  | 最近一次状态修改幂等键 |
| last_status_actor | varchar(36) | YES | NULL |  | 最近一次状态修改主体ID |
| last_status_hash | char(64) | YES | NULL |  | 最近一次状态修改内容摘要 |
| creator_id | varchar(36) | NO |  |  | 创建人ID |
| creator_name | varchar(50) | NO |  |  | 创建人姓名 |
| create_time | datetime(3) | NO | CURRENT_TIMESTAMP(3) | DEFAULT_GENERATED | 创建时间 |
| last_updater_id | varchar(36) | NO |  |  | 最后更新人ID |
| last_updater_name | varchar(50) | NO |  |  | 最后更新人姓名 |
| last_update_time | datetime(3) | NO | CURRENT_TIMESTAMP(3) | DEFAULT_GENERATED on update CURRENT_TIMESTAMP(3) | 最后更新时间 |
| del_flag | char(1) | NO | 0 |  | 删除标志[0-否,1-是] |

索引、外键与完整建表定义（SHOW CREATE TABLE；仅结构）：

```sql
CREATE TABLE `sq_worker_definition` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '存储主键ID',
  `definition_id` char(36) COLLATE utf8mb4_bin NOT NULL COMMENT '能力定义业务ID',
  `worker_key` varchar(80) COLLATE utf8mb4_bin NOT NULL COMMENT '稳定能力键',
  `worker_version` varchar(40) COLLATE utf8mb4_bin NOT NULL COMMENT '不可变能力版本',
  `status` varchar(16) COLLATE utf8mb4_bin NOT NULL COMMENT '状态[ENABLED,DISABLED,REVOKED]',
  `version` bigint NOT NULL DEFAULT '0' COMMENT '状态乐观版本',
  `definition_json` json NOT NULL COMMENT '不可变能力定义',
  `publish_key` varchar(160) COLLATE utf8mb4_bin NOT NULL COMMENT '发布幂等键',
  `payload_hash` char(64) COLLATE utf8mb4_bin NOT NULL COMMENT '发布内容SHA256摘要',
  `last_status_key` varchar(160) COLLATE utf8mb4_bin DEFAULT NULL COMMENT '最近一次状态修改幂等键',
  `last_status_actor` varchar(36) COLLATE utf8mb4_bin DEFAULT NULL COMMENT '最近一次状态修改主体ID',
  `last_status_hash` char(64) COLLATE utf8mb4_bin DEFAULT NULL COMMENT '最近一次状态修改内容摘要',
  `creator_id` varchar(36) COLLATE utf8mb4_bin NOT NULL DEFAULT '' COMMENT '创建人ID',
  `creator_name` varchar(50) COLLATE utf8mb4_bin NOT NULL DEFAULT '' COMMENT '创建人姓名',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `last_updater_id` varchar(36) COLLATE utf8mb4_bin NOT NULL DEFAULT '' COMMENT '最后更新人ID',
  `last_updater_name` varchar(50) COLLATE utf8mb4_bin NOT NULL DEFAULT '' COMMENT '最后更新人姓名',
  `last_update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '最后更新时间',
  `del_flag` char(1) COLLATE utf8mb4_bin NOT NULL DEFAULT '0' COMMENT '删除标志[0-否,1-是]',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_worker_definition_id` (`definition_id`),
  UNIQUE KEY `uk_worker_definition_version` (`worker_key`,`worker_version`),
  UNIQUE KEY `uk_worker_definition_publish` (`creator_id`,`publish_key`),
  KEY `idx_worker_definition_status` (`status`,`id`),
  CONSTRAINT `ck_worker_definition_del` CHECK ((`del_flag` in (_utf8mb4'0',_utf8mb4'1'))),
  CONSTRAINT `ck_worker_definition_status` CHECK ((`status` in (_utf8mb4'ENABLED',_utf8mb4'DISABLED',_utf8mb4'REVOKED')))
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='不可变Worker能力定义';
```

## sq_worker_job

审计字段：完整七字段。

| 列 | 类型 | 可空 | 默认值 | 扩展 | 注释 |
|---|---|---|---|---|---|
| id | bigint | NO | NULL | auto_increment | 存储主键ID |
| job_id | char(36) | NO | NULL |  | 委派节点业务ID |
| user_id | char(36) | NO | NULL |  | 资源归属用户ID |
| task_id | char(36) | NO | NULL |  | 所属目标ID |
| plan_version | bigint | NO | NULL |  | 所属计划版本 |
| node_key | varchar(80) | NO | NULL |  | 计划内节点键 |
| worker_key | varchar(80) | NO | NULL |  | 能力键 |
| worker_version | varchar(40) | NO | NULL |  | 能力版本 |
| status | varchar(24) | NO | NULL |  | BLOCKED/READY/RUNNING/SUCCEEDED/PARTIAL/FAILED/WAITING_INPUT/WAITING_APPROVAL/RECOVERY_BLOCKED/RECONCILING/CANCELLING/CANCELLED/SUPERSEDED |
| depends_on_json | json | YES | NULL |  | 依赖节点键数组 |
| input_json | json | NO | NULL |  | 固定局部输入 |
| result_candidate_json | json | YES | NULL |  | 结果候选（非最终结果） |
| result_json | json | YES | NULL |  | 最终结构化结果 |
| binding_id | char(36) | YES | NULL |  | 运行时绑定ID |
| reused_from_job_id | char(36) | YES | NULL |  | 结果复用来源JobID |
| cancel_requested_at | datetime(3) | YES | NULL |  | 取消屏障时间；新计划替代在途旧Job时写入 |
| version | bigint | NO | 0 |  | 业务版本 |
| creator_id | varchar(36) | NO |  |  | 创建人ID |
| creator_name | varchar(50) | NO |  |  | 创建人姓名 |
| create_time | datetime(3) | NO | CURRENT_TIMESTAMP(3) | DEFAULT_GENERATED | 创建时间 |
| last_updater_id | varchar(36) | NO |  |  | 最后更新人ID |
| last_updater_name | varchar(50) | NO |  |  | 最后更新人姓名 |
| last_update_time | datetime(3) | NO | CURRENT_TIMESTAMP(3) | DEFAULT_GENERATED on update CURRENT_TIMESTAMP(3) | 最后更新时间 |
| del_flag | char(1) | NO | 0 |  | 删除标志[0-否,1-是] |
| guard_token | char(36) | YES | NULL |  | 当前业务执行 guard 令牌 |
| guard_lease_until | datetime(3) | YES | NULL |  | 当前业务 guard 到期时间 |
| candidate_guard_token | char(36) | YES | NULL |  | 提交结果候选时的 guard 令牌 |
| candidate_turn_id | varchar(64) | YES | NULL |  | 提交结果候选的原生回合ID |

索引、外键与完整建表定义（SHOW CREATE TABLE；仅结构）：

```sql
CREATE TABLE `sq_worker_job` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '存储主键ID',
  `job_id` char(36) COLLATE utf8mb4_bin NOT NULL COMMENT '委派节点业务ID',
  `user_id` char(36) COLLATE utf8mb4_bin NOT NULL COMMENT '资源归属用户ID',
  `task_id` char(36) COLLATE utf8mb4_bin NOT NULL COMMENT '所属目标ID',
  `plan_version` bigint NOT NULL COMMENT '所属计划版本',
  `node_key` varchar(80) COLLATE utf8mb4_bin NOT NULL COMMENT '计划内节点键',
  `worker_key` varchar(80) COLLATE utf8mb4_bin NOT NULL COMMENT '能力键',
  `worker_version` varchar(40) COLLATE utf8mb4_bin NOT NULL COMMENT '能力版本',
  `status` varchar(24) COLLATE utf8mb4_bin NOT NULL COMMENT 'BLOCKED/READY/RUNNING/SUCCEEDED/PARTIAL/FAILED/WAITING_INPUT/WAITING_APPROVAL/RECOVERY_BLOCKED/RECONCILING/CANCELLING/CANCELLED/SUPERSEDED',
  `depends_on_json` json DEFAULT NULL COMMENT '依赖节点键数组',
  `input_json` json NOT NULL COMMENT '固定局部输入',
  `result_candidate_json` json DEFAULT NULL COMMENT '结果候选（非最终结果）',
  `result_json` json DEFAULT NULL COMMENT '最终结构化结果',
  `binding_id` char(36) COLLATE utf8mb4_bin DEFAULT NULL COMMENT '运行时绑定ID',
  `reused_from_job_id` char(36) COLLATE utf8mb4_bin DEFAULT NULL COMMENT '结果复用来源JobID',
  `cancel_requested_at` datetime(3) DEFAULT NULL COMMENT '取消屏障时间；新计划替代在途旧Job时写入',
  `version` bigint NOT NULL DEFAULT '0' COMMENT '业务版本',
  `creator_id` varchar(36) COLLATE utf8mb4_bin NOT NULL DEFAULT '' COMMENT '创建人ID',
  `creator_name` varchar(50) COLLATE utf8mb4_bin NOT NULL DEFAULT '' COMMENT '创建人姓名',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `last_updater_id` varchar(36) COLLATE utf8mb4_bin NOT NULL DEFAULT '' COMMENT '最后更新人ID',
  `last_updater_name` varchar(50) COLLATE utf8mb4_bin NOT NULL DEFAULT '' COMMENT '最后更新人姓名',
  `last_update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '最后更新时间',
  `del_flag` char(1) COLLATE utf8mb4_bin NOT NULL DEFAULT '0' COMMENT '删除标志[0-否,1-是]',
  `guard_token` char(36) COLLATE utf8mb4_bin DEFAULT NULL COMMENT '当前业务执行 guard 令牌',
  `guard_lease_until` datetime(3) DEFAULT NULL COMMENT '当前业务 guard 到期时间',
  `candidate_guard_token` char(36) COLLATE utf8mb4_bin DEFAULT NULL COMMENT '提交结果候选时的 guard 令牌',
  `candidate_turn_id` varchar(64) COLLATE utf8mb4_bin DEFAULT NULL COMMENT '提交结果候选的原生回合ID',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_worker_job` (`job_id`),
  UNIQUE KEY `uk_worker_job_node` (`task_id`,`plan_version`,`node_key`),
  KEY `idx_worker_job_status` (`status`,`id`),
  KEY `fk_worker_job_task` (`user_id`,`task_id`),
  CONSTRAINT `fk_worker_job_plan` FOREIGN KEY (`task_id`, `plan_version`) REFERENCES `sq_plan` (`task_id`, `plan_version`),
  CONSTRAINT `fk_worker_job_task` FOREIGN KEY (`user_id`, `task_id`) REFERENCES `sq_goal_task` (`user_id`, `task_id`),
  CONSTRAINT `ck_worker_job_del` CHECK ((`del_flag` in (_utf8mb4'0',_utf8mb4'1'))),
  CONSTRAINT `ck_worker_job_status` CHECK ((`status` in (_utf8mb4'BLOCKED',_utf8mb4'READY',_utf8mb4'RUNNING',_utf8mb4'SUCCEEDED',_utf8mb4'PARTIAL',_utf8mb4'FAILED',_utf8mb4'WAITING_INPUT',_utf8mb4'WAITING_APPROVAL',_utf8mb4'RECOVERY_BLOCKED',_utf8mb4'RECONCILING',_utf8mb4'CANCELLING',_utf8mb4'CANCELLED',_utf8mb4'SUPERSEDED')))
) ENGINE=InnoDB AUTO_INCREMENT=3 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='有界委派节点';
```

## 可执行合并方案与回填边界

1. 以本清单最终 SHOW CREATE TABLE 为基准生成 V1 baseline，移除运行数据相关的 AUTO_INCREMENT 当前值，按外键依赖排序；保留列顺序、类型、默认值、生成列、索引、字符集和中文注释。框架表同样纳入。
2. 逐条核对 V2–V18 的 DML。结构合并不能简单丢弃历史数据转换；新库 baseline 不需要这些转换，已有库需要独立升级路线，不能直接替换已有 Flyway 历史。
3. V8 改为 V2__BackfillConversationExecution：依赖 LegacyConversationBackfill/LegacyMessageBackfillRow；需要最终 conversation、message、execution_scope、response_execution、goal_task、runtime_binding、command、budget 等相关表，保持稳定 UUID、预算身份及幂等行为。
4. V14 改为 V3__BackfillConversationTitles，必须在执行归属回填之后运行；依赖 sq_conversation 的 title_source/title_version/last_activity_at、sq_message 及 sq_goal_task 请求载荷。保留 MANUAL 标题不覆盖规则。
5. 在新隔离 schema 应用 baseline + 两个 Java 回填，再比较 information_schema 列、索引、约束和 SHOW CREATE TABLE。执行完整集成测试与产物链路测试，之后才能删除原增量文件。
6. 不自动重建实际开发库；已有数据库保留原迁移历史，需由用户决定迁移或重建方式。
