-- 小文件内容与元数据原子提交；沙箱受控交付及物理清理由后续阶段扩展。
CREATE TABLE sq_artifact (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '存储主键ID',
    artifact_id CHAR(36) NOT NULL COMMENT '产物业务ID',
    user_id CHAR(36) NOT NULL COMMENT '归属用户ID',
    file_name VARCHAR(240) NOT NULL COMMENT '安全展示文件名',
    media_type VARCHAR(120) NOT NULL COMMENT '服务端判定媒体类型',
    size_bytes BIGINT NOT NULL COMMENT '内容字节数',
    sha256 CHAR(64) NOT NULL COMMENT '内容SHA256摘要',
    upload_key VARCHAR(160) NOT NULL COMMENT '归属用户内的上传幂等键',
    content_bytes LONGBLOB NOT NULL COMMENT '已校验的小文件内容',
    expires_at DATETIME(3) NOT NULL COMMENT '读取有效期',
    creator_id VARCHAR(36) NOT NULL DEFAULT '' COMMENT '创建人ID',
    creator_name VARCHAR(50) NOT NULL DEFAULT '' COMMENT '创建人姓名',
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    last_updater_id VARCHAR(36) NOT NULL DEFAULT '' COMMENT '最后更新人ID',
    last_updater_name VARCHAR(50) NOT NULL DEFAULT '' COMMENT '最后更新人姓名',
    last_update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '最后更新时间',
    del_flag CHAR(1) NOT NULL DEFAULT '0' COMMENT '删除标志[0-否,1-是]',
    PRIMARY KEY (id),
    UNIQUE KEY uk_artifact (artifact_id),
    UNIQUE KEY uk_artifact_owner (user_id, artifact_id),
    UNIQUE KEY uk_artifact_upload (user_id, upload_key),
    CONSTRAINT ck_artifact_size CHECK (size_bytes >= 0 AND size_bytes <= 10485760)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='小文件产物';

CREATE TABLE sq_task_artifact (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '存储主键ID',
    user_id CHAR(36) NOT NULL COMMENT '归属用户ID',
    task_id CHAR(36) NOT NULL COMMENT '关联目标ID',
    artifact_id CHAR(36) NOT NULL COMMENT '关联产物ID',
    creator_id VARCHAR(36) NOT NULL DEFAULT '' COMMENT '创建人ID',
    creator_name VARCHAR(50) NOT NULL DEFAULT '' COMMENT '创建人姓名',
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    last_updater_id VARCHAR(36) NOT NULL DEFAULT '' COMMENT '最后更新人ID',
    last_updater_name VARCHAR(50) NOT NULL DEFAULT '' COMMENT '最后更新人姓名',
    last_update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '最后更新时间',
    del_flag CHAR(1) NOT NULL DEFAULT '0' COMMENT '删除标志[0-否,1-是]',
    PRIMARY KEY (id),
    UNIQUE KEY uk_task_artifact (task_id, artifact_id),
    CONSTRAINT fk_task_artifact_task FOREIGN KEY (user_id, task_id) REFERENCES sq_goal_task (user_id, task_id),
    CONSTRAINT fk_task_artifact_owner FOREIGN KEY (user_id, artifact_id) REFERENCES sq_artifact (user_id, artifact_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='目标与产物归属关联';

CREATE TABLE sq_operation_reconciler (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '存储主键ID',
    user_id CHAR(36) NOT NULL COMMENT '被授权核对用户ID',
    task_id CHAR(36) NOT NULL COMMENT '核对授权目标范围',
    creator_id VARCHAR(36) NOT NULL DEFAULT '' COMMENT '创建人ID',
    creator_name VARCHAR(50) NOT NULL DEFAULT '' COMMENT '创建人姓名',
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    last_updater_id VARCHAR(36) NOT NULL DEFAULT '' COMMENT '最后更新人ID',
    last_updater_name VARCHAR(50) NOT NULL DEFAULT '' COMMENT '最后更新人姓名',
    last_update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '最后更新时间',
    del_flag CHAR(1) NOT NULL DEFAULT '0' COMMENT '删除标志[0-否,1-是]',
    PRIMARY KEY (id),
    UNIQUE KEY uk_operation_reconciler (user_id, task_id),
    CONSTRAINT fk_reconciler_task FOREIGN KEY (user_id, task_id) REFERENCES sq_goal_task (user_id, task_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='操作核对显式授权范围';
