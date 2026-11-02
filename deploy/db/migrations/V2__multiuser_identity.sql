-- 生产增量迁移 V2：多用户身份与认证（C1）
-- 前置版本：V1__initial_44_tables.sql（冻结的 44 表基线快照）。
-- 本脚本只前向增加 4 张表，不修改既有表，不包含任何 DROP/DELETE。
-- 已存在业务数据的库须先人工备份并核对表/列/索引一致性，再由操作者显式 baseline 与 apply。

CREATE TABLE `sq_user`
(
    `id`                   bigint      NOT NULL AUTO_INCREMENT COMMENT '存储主键ID',
    `user_id`              char(36)    NOT NULL COMMENT '用户业务ID',
    `username`             varchar(64) NOT NULL COMMENT '规范化用户名（小写）',
    `display_name`         varchar(100) NOT NULL DEFAULT '' COMMENT '显示名称',
    `password_hash`        varchar(255) NOT NULL COMMENT 'BCrypt密码哈希',
    `role`                 varchar(16) NOT NULL DEFAULT 'USER' COMMENT '角色[USER,ADMIN]',
    `status`               varchar(16) NOT NULL DEFAULT 'ACTIVE' COMMENT '状态[ACTIVE,DISABLED]',
    `auth_version`         bigint      NOT NULL DEFAULT '0' COMMENT '授权撤销代际',
    `must_change_password` tinyint(1)  NOT NULL DEFAULT '1' COMMENT '是否必须首次登录修改密码',
    `version`              bigint      NOT NULL DEFAULT '0' COMMENT '状态乐观版本',
    `creator_id`           varchar(36) NOT NULL DEFAULT '' COMMENT '创建人ID',
    `creator_name`         varchar(50) NOT NULL DEFAULT '' COMMENT '创建人姓名',
    `create_time`          datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    `last_updater_id`      varchar(36) NOT NULL DEFAULT '' COMMENT '最后更新人ID',
    `last_updater_name`    varchar(50) NOT NULL DEFAULT '' COMMENT '最后更新人姓名',
    `last_update_time`     datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '最后更新时间',
    `del_flag`             char(1)     NOT NULL DEFAULT '0' COMMENT '删除标志[0-否,1-是]',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_id` (`user_id`),
    UNIQUE KEY `uk_user_username` (`username`),
    KEY `idx_user_role_status` (`role`, `status`, `id`)
) ENGINE = InnoDB
    COMMENT ='用户身份';

CREATE TABLE `sq_auth_session`
(
    `id`                  bigint      NOT NULL AUTO_INCREMENT COMMENT '存储主键ID',
    `session_id`          char(36)    NOT NULL COMMENT '会话业务ID',
    `user_id`             char(36)    NOT NULL COMMENT '所属用户ID',
    `token_hash`          char(64)    NOT NULL COMMENT '会话令牌SHA256摘要',
    `csrf_hash`           char(64)    NOT NULL COMMENT 'CSRF令牌SHA256摘要',
    `issued_auth_version` bigint      NOT NULL COMMENT '签发时会话授权代际',
    `last_seen_at`        datetime(3) NOT NULL COMMENT '最近活跃时间',
    `idle_expires_at`     datetime(3) NOT NULL COMMENT '空闲过期时间',
    `absolute_expires_at` datetime(3) NOT NULL COMMENT '绝对过期时间',
    `revoked_at`          datetime(3)          DEFAULT NULL COMMENT '撤销时间',
    `creator_id`          varchar(36) NOT NULL DEFAULT '' COMMENT '创建人ID',
    `creator_name`        varchar(50) NOT NULL DEFAULT '' COMMENT '创建人姓名',
    `create_time`         datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    `last_updater_id`     varchar(36) NOT NULL DEFAULT '' COMMENT '最后更新人ID',
    `last_updater_name`   varchar(50) NOT NULL DEFAULT '' COMMENT '最后更新人姓名',
    `last_update_time`    datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '最后更新时间',
    `del_flag`            char(1)     NOT NULL DEFAULT '0' COMMENT '删除标志[0-否,1-是]',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_auth_session_id` (`session_id`),
    UNIQUE KEY `uk_auth_session_token` (`token_hash`),
    KEY `idx_auth_session_user` (`user_id`, `revoked_at`),
    KEY `idx_auth_session_idle` (`idle_expires_at`),
    KEY `idx_auth_session_absolute` (`absolute_expires_at`)
) ENGINE = InnoDB
    COMMENT ='持久登录会话';

CREATE TABLE `sq_auth_rate_bucket`
(
    `id`               bigint      NOT NULL AUTO_INCREMENT COMMENT '存储主键ID',
    `bucket_key`       char(64)    NOT NULL COMMENT '用户名与来源派生的不可逆限流键',
    `window_start`     datetime(3) NOT NULL COMMENT '当前失败窗口开始时间',
    `failures`         int         NOT NULL DEFAULT '0' COMMENT '窗口内失败次数',
    `locked_until`     datetime(3)          DEFAULT NULL COMMENT '锁定截止时间',
    `creator_id`       varchar(36) NOT NULL DEFAULT '' COMMENT '创建人ID',
    `creator_name`     varchar(50) NOT NULL DEFAULT '' COMMENT '创建人姓名',
    `create_time`      datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    `last_updater_id`  varchar(36) NOT NULL DEFAULT '' COMMENT '最后更新人ID',
    `last_updater_name` varchar(50) NOT NULL DEFAULT '' COMMENT '最后更新人姓名',
    `last_update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '最后更新时间',
    `del_flag`         char(1)     NOT NULL DEFAULT '0' COMMENT '删除标志[0-否,1-是]',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_auth_rate_bucket` (`bucket_key`),
    KEY `idx_auth_rate_locked` (`locked_until`)
) ENGINE = InnoDB
    COMMENT ='登录失败限流状态';

CREATE TABLE `sq_auth_audit`
(
    `id`               bigint      NOT NULL AUTO_INCREMENT COMMENT '存储主键ID',
    `event_id`         char(36)    NOT NULL COMMENT '审计事件业务ID',
    `user_id`          char(36)             DEFAULT NULL COMMENT '目标用户ID',
    `actor_user_id`    char(36)             DEFAULT NULL COMMENT '操作者用户ID',
    `event_type`       varchar(48) NOT NULL COMMENT '事件类型',
    `result`           varchar(16) NOT NULL COMMENT '结果[SUCCESS,FAILURE,DENIED]',
    `occurred_at`      datetime(3) NOT NULL COMMENT '事件发生时间',
    `request_id`       varchar(64) NOT NULL DEFAULT '' COMMENT '请求追踪ID',
    `creator_id`       varchar(36) NOT NULL DEFAULT '' COMMENT '创建人ID',
    `creator_name`     varchar(50) NOT NULL DEFAULT '' COMMENT '创建人姓名',
    `create_time`      datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    `last_updater_id`  varchar(36) NOT NULL DEFAULT '' COMMENT '最后更新人ID',
    `last_updater_name` varchar(50) NOT NULL DEFAULT '' COMMENT '最后更新人姓名',
    `last_update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '最后更新时间',
    `del_flag`         char(1)     NOT NULL DEFAULT '0' COMMENT '删除标志[0-否,1-是]',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_auth_audit_event` (`event_id`),
    KEY `idx_auth_audit_user` (`user_id`, `occurred_at`),
    KEY `idx_auth_audit_type` (`event_type`, `occurred_at`)
) ENGINE = InnoDB
    COMMENT ='认证安全审计（不存令牌、明文密码和完整IP）';
