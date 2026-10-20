-- 原生日志对象使用版本 CAS；命令投递租约只保护平台投递，不替代 AgentScope writer lease。
CREATE TABLE sq_native_session_object
(
    id               BIGINT                           NOT NULL AUTO_INCREMENT COMMENT '存储主键ID',
    object_path      VARCHAR(768) COLLATE utf8mb4_bin NOT NULL COMMENT 'AgentScope 原生日志对象路径',
    object_version   BIGINT                           NOT NULL COMMENT '原生日志对象 CAS 版本',
    object_bytes     MEDIUMBLOB                       NOT NULL COMMENT '原生日志原始字节',
    create_time      DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    last_update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '最后更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_native_object_path (object_path)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='AgentScope 原生会话日志对象';

ALTER TABLE sq_command ADD COLUMN delivery_token CHAR(36) NULL COMMENT '当前投递领取令牌';
ALTER TABLE sq_command ADD COLUMN delivery_lease_until DATETIME(3) NULL COMMENT '投递令牌过期时间';
ALTER TABLE sq_command ADD COLUMN delivery_attempts INT NOT NULL DEFAULT 0 COMMENT '投递尝试次数';
ALTER TABLE sq_command ADD COLUMN native_turn_id VARCHAR(64) NULL COMMENT 'AgentScope 原生回合ID';
ALTER TABLE sq_command ADD COLUMN native_run_id VARCHAR(64) NULL COMMENT 'AgentScope 原生运行ID';
ALTER TABLE sq_command ADD COLUMN native_status VARCHAR(24) NULL COMMENT 'AgentScope 提交时状态';
ALTER TABLE sq_command ADD COLUMN last_error_code VARCHAR(64) NULL COMMENT '投递错误分类，不存异常详情';
