-- 用户外部连接凭据引用与本地密钥保险库；表内不保存明文密钥。
CREATE TABLE sq_connection
(
    id                     BIGINT       NOT NULL AUTO_INCREMENT COMMENT '存储主键ID',
    connection_id          CHAR(36)     NOT NULL COMMENT '连接业务ID',
    user_id                CHAR(36)     NOT NULL COMMENT '资源归属用户ID',
    name                   VARCHAR(100) NOT NULL COMMENT '显示名称',
    provider               VARCHAR(80)  NOT NULL COMMENT '提供方编码',
    environment            VARCHAR(80)  NOT NULL COMMENT '环境标识',
    resource_policy_json   JSON         NOT NULL COMMENT '受控资源范围',
    secret_ref             VARCHAR(240) NOT NULL COMMENT '密钥管理引用；不含明文',
    create_idempotency_key VARCHAR(160) NULL COMMENT '创建幂等键；同键同用户复用原连接',
    revision               BIGINT       NOT NULL DEFAULT 0 COMMENT '配置授权版本',
    enabled                TINYINT      NOT NULL DEFAULT 1 COMMENT '是否启用[0/1]',
    creator_id             VARCHAR(36)  NOT NULL DEFAULT '' COMMENT '创建人ID',
    creator_name           VARCHAR(50)  NOT NULL DEFAULT '' COMMENT '创建人姓名',
    create_time            DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    last_updater_id        VARCHAR(36)  NOT NULL DEFAULT '' COMMENT '最后更新人ID',
    last_updater_name      VARCHAR(50)  NOT NULL DEFAULT '' COMMENT '最后更新人姓名',
    last_update_time       DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '最后更新时间',
    del_flag               CHAR(1)      NOT NULL DEFAULT '0' COMMENT '删除标志[0-否,1-是]',
    PRIMARY KEY (id),
    UNIQUE KEY uk_owner_connection (user_id, connection_id),
    UNIQUE KEY uk_owner_create_idem (user_id, create_idempotency_key),
    CONSTRAINT ck_connection_del CHECK (del_flag IN ('0', '1'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='用户外部连接凭据引用';

CREATE TABLE sq_secret_vault
(
    id           BIGINT       NOT NULL AUTO_INCREMENT COMMENT '存储主键ID',
    secret_ref   VARCHAR(240) NOT NULL COMMENT '密钥引用；格式 local-vault:keyId:uuid',
    key_id       VARCHAR(64)  NOT NULL COMMENT '加密密钥版本ID；轮换期间新旧并存',
    cipher_bytes VARBINARY(2048) NOT NULL COMMENT 'AES-GCM 密文；IV 前置',
    create_time  DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_secret_ref (secret_ref)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='本地密钥保险库密文；不保存明文';
