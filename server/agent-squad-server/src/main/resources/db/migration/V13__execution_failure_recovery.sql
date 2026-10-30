CREATE TABLE sq_execution_failure
(
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '存储主键ID',
    failure_id CHAR(36) NOT NULL COMMENT '故障业务ID',
    user_id CHAR(36) NOT NULL COMMENT '归属用户ID',
    scope_id CHAR(36) NOT NULL COMMENT '受权执行作用域ID',
    binding_id CHAR(36) NOT NULL COMMENT '故障来源原生绑定ID',
    failure_key CHAR(64) NOT NULL COMMENT '稳定故障来源摘要',
    category VARCHAR(32) NOT NULL COMMENT '故障分类',
    code VARCHAR(64) NOT NULL COMMENT '稳定原因码',
    actor VARCHAR(16) NOT NULL COMMENT '处理责任[SYSTEM,USER,OPERATOR]',
    safe_message VARCHAR(500) NOT NULL COMMENT '面向用户的安全说明',
    diagnostic_id CHAR(36) NOT NULL COMMENT '内部诊断关联ID，不含原始异常',
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE' COMMENT '状态[ACTIVE,RETRYING,RESOLVED]',
    retry_attempt INT NOT NULL DEFAULT 0 COMMENT '该故障已批准的固定重试序号，避免并行执行改变计费身份',
    creator_id VARCHAR(36) NOT NULL DEFAULT '' COMMENT '创建人ID',
    creator_name VARCHAR(50) NOT NULL DEFAULT '' COMMENT '创建人姓名',
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    last_updater_id VARCHAR(36) NOT NULL DEFAULT '' COMMENT '最后更新人ID',
    last_updater_name VARCHAR(50) NOT NULL DEFAULT '' COMMENT '最后更新人姓名',
    last_update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '最后更新时间',
    del_flag CHAR(1) NOT NULL DEFAULT '0' COMMENT '删除标志[0-否,1-是]',
    PRIMARY KEY (id),
    UNIQUE KEY uk_execution_failure (user_id, failure_id),
    UNIQUE KEY uk_execution_failure_source (user_id, scope_id, failure_key),
    KEY idx_failure_scope (user_id, scope_id, id),
    CONSTRAINT fk_failure_scope FOREIGN KEY (user_id, scope_id) REFERENCES sq_execution_scope (user_id, scope_id),
    CONSTRAINT ck_failure_status CHECK (status IN ('ACTIVE','RETRYING','RESOLVED')),
    CONSTRAINT ck_failure_del CHECK (del_flag IN ('0','1'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='执行的结构化故障事实';

ALTER TABLE sq_execution_scope ADD COLUMN service_retry_attempts INT NOT NULL DEFAULT 0 COMMENT '已持久自动服务重试次数，不因重启清零';
ALTER TABLE sq_execution_scope ADD COLUMN plan_correction_attempts INT NOT NULL DEFAULT 0 COMMENT '无效计划次数，首次失败后最多两次纠正';
ALTER TABLE sq_execution_scope ADD COLUMN retry_next_attempt_at DATETIME(3) NULL COMMENT '已排队恢复的执行时间';
ALTER TABLE sq_response_execution ADD UNIQUE KEY uk_response_successor (user_id, retry_of_execution_id);

ALTER TABLE sq_response_execution ADD COLUMN dispatch_sequence BIGINT NULL COMMENT '回复领取顺序，显式重试排在已受理执行之后';
UPDATE sq_response_execution
SET dispatch_sequence = (SELECT m.message_seq FROM sq_message m
    WHERE m.user_id = sq_response_execution.user_id AND m.message_id = sq_response_execution.source_message_id)
WHERE dispatch_sequence IS NULL;

ALTER TABLE sq_conversation MODIFY COLUMN title VARCHAR(200) NOT NULL COMMENT '用户对话标题';
