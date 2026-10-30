-- D4 个人技能版本、授权与执行固定引用；不修改既有会话与运行身份。
CREATE TABLE sq_skill_version
(
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '存储主键ID',
    skill_version_id CHAR(36) NOT NULL COMMENT '技能版本业务ID',
    user_id          CHAR(36) NOT NULL COMMENT '所属用户ID',
    skill_name       VARCHAR(100) NOT NULL COMMENT '用户内技能名称',
    skill_version    VARCHAR(80) NOT NULL COMMENT '技能内容版本',
    description      VARCHAR(4000) NOT NULL COMMENT '技能目录描述',
    content_hash     CHAR(64) NOT NULL COMMENT '规范化文本包SHA256摘要',
    required_tools_json JSON NOT NULL COMMENT '使用技能所需工具名称列表',
    status           VARCHAR(20) NOT NULL DEFAULT 'ENABLED' COMMENT '版本状态[ENABLED,REVOKED]',
    authorization_revision BIGINT NOT NULL DEFAULT 0 COMMENT '安全授权代际',
    creator_id       VARCHAR(36) NOT NULL DEFAULT '' COMMENT '创建人ID',
    creator_name     VARCHAR(50) NOT NULL DEFAULT '' COMMENT '创建人姓名',
    create_time      DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    last_updater_id   VARCHAR(36) NOT NULL DEFAULT '' COMMENT '最后更新人ID',
    last_updater_name VARCHAR(50) NOT NULL DEFAULT '' COMMENT '最后更新人姓名',
    last_update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '最后更新时间',
    del_flag         CHAR(1) NOT NULL DEFAULT '0' COMMENT '删除标志[0-否,1-是]',
    PRIMARY KEY (id),
    UNIQUE KEY uk_skill_version (user_id, skill_version_id),
    UNIQUE KEY uk_skill_name_version (user_id, skill_name, skill_version),
    CONSTRAINT ck_skill_version_status CHECK (status IN ('ENABLED', 'REVOKED')),
    CONSTRAINT ck_skill_version_del CHECK (del_flag IN ('0', '1'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='用户技能不可变版本';

CREATE TABLE sq_skill_file
(
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '存储主键ID',
    skill_file_id    CHAR(36) NOT NULL COMMENT '技能文件业务ID',
    user_id          CHAR(36) NOT NULL COMMENT '所属用户ID',
    skill_version_id CHAR(36) NOT NULL COMMENT '所属技能版本ID',
    file_path        VARCHAR(240) NOT NULL COMMENT '包内规范化相对路径',
    file_content     MEDIUMTEXT NOT NULL COMMENT 'UTF8文本正文',
    creator_id       VARCHAR(36) NOT NULL DEFAULT '' COMMENT '创建人ID',
    creator_name     VARCHAR(50) NOT NULL DEFAULT '' COMMENT '创建人姓名',
    create_time      DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    last_updater_id   VARCHAR(36) NOT NULL DEFAULT '' COMMENT '最后更新人ID',
    last_updater_name VARCHAR(50) NOT NULL DEFAULT '' COMMENT '最后更新人姓名',
    last_update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '最后更新时间',
    del_flag         CHAR(1) NOT NULL DEFAULT '0' COMMENT '删除标志[0-否,1-是]',
    PRIMARY KEY (id),
    UNIQUE KEY uk_skill_file_id (user_id, skill_file_id),
    UNIQUE KEY uk_skill_file_path (user_id, skill_version_id, file_path),
    CONSTRAINT fk_skill_file_version FOREIGN KEY (user_id, skill_version_id) REFERENCES sq_skill_version (user_id, skill_version_id),
    CONSTRAINT ck_skill_file_del CHECK (del_flag IN ('0', '1'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='技能版本只读文本文件';

CREATE TABLE sq_skill_grant
(
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '存储主键ID',
    grant_id         CHAR(36) NOT NULL COMMENT '技能授权业务ID',
    user_id          CHAR(36) NOT NULL COMMENT '授权用户ID',
    skill_version_id CHAR(36) NOT NULL COMMENT '被授权技能版本ID',
    enabled          BOOLEAN NOT NULL DEFAULT FALSE COMMENT '是否允许新执行选择',
    version          BIGINT NOT NULL DEFAULT 0 COMMENT '授权乐观版本',
    creator_id       VARCHAR(36) NOT NULL DEFAULT '' COMMENT '创建人ID',
    creator_name     VARCHAR(50) NOT NULL DEFAULT '' COMMENT '创建人姓名',
    create_time      DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    last_updater_id   VARCHAR(36) NOT NULL DEFAULT '' COMMENT '最后更新人ID',
    last_updater_name VARCHAR(50) NOT NULL DEFAULT '' COMMENT '最后更新人姓名',
    last_update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '最后更新时间',
    del_flag         CHAR(1) NOT NULL DEFAULT '0' COMMENT '删除标志[0-否,1-是]',
    PRIMARY KEY (id),
    UNIQUE KEY uk_skill_grant_id (user_id, grant_id),
    UNIQUE KEY uk_skill_grant_version (user_id, skill_version_id),
    CONSTRAINT fk_skill_grant_version FOREIGN KEY (user_id, skill_version_id) REFERENCES sq_skill_version (user_id, skill_version_id),
    CONSTRAINT ck_skill_grant_del CHECK (del_flag IN ('0', '1'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='用户技能版本使用授权';

CREATE TABLE sq_scope_skill_catalog
(
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '存储主键ID',
    catalog_id       CHAR(36) NOT NULL COMMENT '固定目录业务ID',
    user_id          CHAR(36) NOT NULL COMMENT '资源所属用户ID',
    scope_id         CHAR(36) NOT NULL COMMENT '固定目录执行作用域ID',
    creator_id       VARCHAR(36) NOT NULL DEFAULT '' COMMENT '创建人ID',
    creator_name     VARCHAR(50) NOT NULL DEFAULT '' COMMENT '创建人姓名',
    create_time      DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    last_updater_id   VARCHAR(36) NOT NULL DEFAULT '' COMMENT '最后更新人ID',
    last_updater_name VARCHAR(50) NOT NULL DEFAULT '' COMMENT '最后更新人姓名',
    last_update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '最后更新时间',
    del_flag         CHAR(1) NOT NULL DEFAULT '0' COMMENT '删除标志[0-否,1-是]',
    PRIMARY KEY (id),
    UNIQUE KEY uk_scope_skill_catalog_id (user_id, catalog_id),
    UNIQUE KEY uk_scope_skill_catalog (user_id, scope_id),
    CONSTRAINT fk_skill_catalog_scope FOREIGN KEY (user_id, scope_id) REFERENCES sq_execution_scope (user_id, scope_id),
    CONSTRAINT ck_scope_skill_catalog_del CHECK (del_flag IN ('0', '1'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='执行作用域固定技能目录';

CREATE TABLE sq_scope_skill_ref
(
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '存储主键ID',
    reference_id     CHAR(36) NOT NULL COMMENT '技能引用业务ID',
    user_id          CHAR(36) NOT NULL COMMENT '资源所属用户ID',
    scope_id         CHAR(36) NOT NULL COMMENT '固定引用执行作用域ID',
    skill_version_id CHAR(36) NOT NULL COMMENT '固定技能版本ID',
    content_hash     CHAR(64) NOT NULL COMMENT '固定技能内容摘要',
    authorization_revision BIGINT NOT NULL COMMENT '固定时的安全授权代际',
    creator_id       VARCHAR(36) NOT NULL DEFAULT '' COMMENT '创建人ID',
    creator_name     VARCHAR(50) NOT NULL DEFAULT '' COMMENT '创建人姓名',
    create_time      DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    last_updater_id   VARCHAR(36) NOT NULL DEFAULT '' COMMENT '最后更新人ID',
    last_updater_name VARCHAR(50) NOT NULL DEFAULT '' COMMENT '最后更新人姓名',
    last_update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '最后更新时间',
    del_flag         CHAR(1) NOT NULL DEFAULT '0' COMMENT '删除标志[0-否,1-是]',
    PRIMARY KEY (id),
    UNIQUE KEY uk_scope_skill_ref_id (user_id, reference_id),
    UNIQUE KEY uk_scope_skill_ref (user_id, scope_id, skill_version_id),
    CONSTRAINT fk_scope_skill_ref_catalog FOREIGN KEY (user_id, scope_id) REFERENCES sq_scope_skill_catalog (user_id, scope_id),
    CONSTRAINT fk_scope_skill_ref_version FOREIGN KEY (user_id, skill_version_id) REFERENCES sq_skill_version (user_id, skill_version_id),
    CONSTRAINT ck_scope_skill_ref_del CHECK (del_flag IN ('0', '1'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='执行作用域固定技能版本引用';

