-- C2：跨实例持久原生运行配额。仅在执行独立迁移后才能启用 admission。
-- 全局和用户计数在同一事务锁定；无法确认结束的原生运行必须保留 ACTIVE 占位。

CREATE TABLE IF NOT EXISTS sq_runtime_admission_counter
(
    pool_key     VARCHAR(80) NOT NULL COMMENT 'GLOBAL或USER:<user_id>',
    active_count INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '当前活跃占位数量',
    PRIMARY KEY (pool_key)
) ENGINE = InnoDB COMMENT = '运行准入持久配额计数';

CREATE TABLE IF NOT EXISTS sq_runtime_admission_slot
(
    binding_id   CHAR(36)    NOT NULL COMMENT '固定原生会话绑定标识',
    user_id      CHAR(36)    NOT NULL COMMENT '归属用户标识',
    command_id   CHAR(36)    NOT NULL COMMENT '持有占位的原始命令标识',
    action       VARCHAR(32) NOT NULL COMMENT '原始命令动作',
    turn_id      VARCHAR(64)          DEFAULT NULL COMMENT '原生执行回合ID',
    status       VARCHAR(16) NOT NULL COMMENT 'ACTIVE或RELEASED',
    acquired_at  DATETIME(3) NOT NULL COMMENT '占位获取时间UTC',
    released_at  DATETIME(3)          DEFAULT NULL COMMENT '占位释放时间UTC',
    PRIMARY KEY (binding_id),
    KEY idx_admission_slot_user (user_id, status),
    KEY idx_admission_slot_command (command_id)
) ENGINE = InnoDB COMMENT = '绑定级原生执行容量占位';
