-- C1：真实多用户账号与跨网关会话。生产数据库由独立受控迁移操作执行。
CREATE TABLE IF NOT EXISTS sq_user (
 id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '存储主键',
 user_id CHAR(36) NOT NULL COMMENT '用户业务ID',
 username VARCHAR(64) NOT NULL COMMENT '归一化登录名',
 display_name VARCHAR(100) NOT NULL DEFAULT '' COMMENT '展示名称',
 password_hash VARCHAR(255) NOT NULL COMMENT 'BCrypt密码摘要',
 role VARCHAR(16) NOT NULL DEFAULT 'USER' COMMENT 'USER或ADMIN',
 status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE或DISABLED',
 auth_version BIGINT NOT NULL DEFAULT 0 COMMENT '认证撤销代际',
 version BIGINT NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
 must_change_password TINYINT(1) NOT NULL DEFAULT 1 COMMENT '首次需修改密码',
 creator_id VARCHAR(36) NOT NULL DEFAULT '' COMMENT '创建者',
 creator_name VARCHAR(50) NOT NULL DEFAULT '' COMMENT '创建者名',
 create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
 last_updater_id VARCHAR(36) NOT NULL DEFAULT '' COMMENT '修改者',
 last_updater_name VARCHAR(50) NOT NULL DEFAULT '' COMMENT '修改者名',
 last_update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '修改时间',
 del_flag CHAR(1) NOT NULL DEFAULT '0' COMMENT '删除标记',
 PRIMARY KEY (id), UNIQUE KEY uk_user_user_id(user_id), UNIQUE KEY uk_user_username(username)
) ENGINE=InnoDB COMMENT='账号表';

CREATE TABLE IF NOT EXISTS sq_auth_session (
 id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '存储主键',
 session_id CHAR(36) NOT NULL COMMENT '登录会话ID',
 user_id CHAR(36) NOT NULL COMMENT '账号ID',
 token_hash CHAR(64) NOT NULL COMMENT 'Token SHA256 摘要',
 csrf_hash CHAR(64) NOT NULL COMMENT 'CSRF SHA256 摘要',
 issued_auth_version BIGINT NOT NULL COMMENT '颁发时认证版本',
 created_at DATETIME(3) NOT NULL COMMENT '创建时间',
 last_seen_at DATETIME(3) NOT NULL COMMENT '最近访问',
 idle_expires_at DATETIME(3) NOT NULL COMMENT '空闲过期时间',
 absolute_expires_at DATETIME(3) NOT NULL COMMENT '绝对过期时间',
 revoked_at DATETIME(3) NULL COMMENT '撤销时间',
 PRIMARY KEY(id), UNIQUE KEY uk_auth_session_id(session_id),
 UNIQUE KEY uk_auth_token_hash(token_hash), KEY idx_auth_session_user(user_id,revoked_at),
 KEY idx_auth_session_expiry(idle_expires_at)
) ENGINE=InnoDB COMMENT='认证持久会话';

CREATE TABLE IF NOT EXISTS sq_auth_rate_bucket (
 id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '存储主键',
 bucket_key CHAR(64) NOT NULL COMMENT '归一化身份标识摘要',
 window_start DATETIME(3) NOT NULL COMMENT '窗口开始',
 failures INT NOT NULL DEFAULT 0 COMMENT '失败次数',
 locked_until DATETIME(3) NULL COMMENT '封禁到期',
 PRIMARY KEY(id), UNIQUE KEY uk_auth_bucket(bucket_key)
) ENGINE=InnoDB COMMENT='认证失败节流';

CREATE TABLE IF NOT EXISTS sq_auth_audit (
 id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '存储主键',
 event_id CHAR(36) NOT NULL COMMENT '事件ID',
 user_id CHAR(36) NULL COMMENT '受影响用户',
 actor_user_id CHAR(36) NULL COMMENT '操作用户',
 event_type VARCHAR(48) NOT NULL COMMENT '事件类型',
 result VARCHAR(16) NOT NULL COMMENT '操作结果',
 occurred_at DATETIME(3) NOT NULL COMMENT '事件时间',
 request_id VARCHAR(64) NOT NULL DEFAULT '' COMMENT '请求关联ID',
 PRIMARY KEY(id), UNIQUE KEY uk_auth_audit_id(event_id),
 KEY idx_auth_audit_user(user_id, occurred_at)
) ENGINE=InnoDB COMMENT='认证安全审计';
