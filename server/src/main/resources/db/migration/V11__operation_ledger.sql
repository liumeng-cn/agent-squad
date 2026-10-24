-- D2 受控工具：外部副作用账本、预算预留与本地模拟外部系统。
CREATE TABLE sq_operation
(
    id                  BIGINT       NOT NULL AUTO_INCREMENT COMMENT '存储主键ID',
    operation_id        CHAR(36)     NOT NULL COMMENT '操作ID',
    user_id             CHAR(36)     NOT NULL COMMENT '资源归属用户ID',
    task_id             CHAR(36)     NOT NULL COMMENT '所属目标ID',
    job_id              CHAR(36) NULL COMMENT '节点ID；Master可空',
    binding_id          CHAR(36)     NOT NULL COMMENT '原生调用所属绑定',
    framework_turn_id   VARCHAR(200) NOT NULL COMMENT '原生TurnID',
    tool_call_id        VARCHAR(200) NOT NULL COMMENT '原生稳定工具调用ID',
    tool_key            VARCHAR(100) NOT NULL COMMENT '工具键',
    tool_version        VARCHAR(40)  NOT NULL COMMENT '工具版本',
    business_intent_key VARCHAR(160) NULL COMMENT '业务写入幂等键；READ可空',
    effect              VARCHAR(16)  NOT NULL COMMENT 'READ/WRITE/SANDBOX',
    connection_id       CHAR(36) NULL COMMENT '用户连接ID',
    connection_revision BIGINT       NOT NULL DEFAULT 0 COMMENT '派发时连接版本',
    auth_version        BIGINT       NOT NULL DEFAULT 0 COMMENT '派发时授权版本',
    request_hash        VARCHAR(64)  NOT NULL COMMENT '规范化参数摘要',
    arguments_json      JSON         NOT NULL COMMENT '脱敏参数；敏感值用引用',
    status              VARCHAR(24)  NOT NULL COMMENT 'PREPARED/WAITING_APPROVAL/IN_FLIGHT/UNKNOWN/RECONCILING/SUCCEEDED/FAILED/CANCELLED',
    version             BIGINT       NOT NULL DEFAULT 0 COMMENT '业务乐观版本',
    executor_owner      VARCHAR(100) NULL COMMENT '独立执行器实例',
    executor_fence      BIGINT       NOT NULL DEFAULT 0 COMMENT '独立执行器栅栏',
    executor_until      DATETIME(3)   NULL COMMENT '执行器租约',
    external_key        VARCHAR(200) NULL COMMENT '外部幂等或查询关联号',
    result_json         JSON NULL COMMENT '可信连接器结果或核对事实',
    creator_id          VARCHAR(36)  NOT NULL DEFAULT '' COMMENT '创建人ID',
    creator_name        VARCHAR(50)  NOT NULL DEFAULT '' COMMENT '创建人姓名',
    create_time         DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    last_updater_id     VARCHAR(36)  NOT NULL DEFAULT '' COMMENT '最后更新人ID',
    last_updater_name   VARCHAR(50)  NOT NULL DEFAULT '' COMMENT '最后更新人姓名',
    last_update_time    DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '最后更新时间',
    del_flag            CHAR(1)      NOT NULL DEFAULT '0' COMMENT '删除标志[0-否,1-是]',
    PRIMARY KEY (id),
    UNIQUE KEY uk_owner_operation (user_id, operation_id),
    UNIQUE KEY uk_native_call (user_id, binding_id, framework_turn_id, tool_call_id),
    UNIQUE KEY uk_business_intent (user_id, task_id, tool_key, business_intent_key),
    KEY                 idx_operation_unknown (status, last_update_time),
    CONSTRAINT ck_operation_del CHECK (del_flag IN ('0', '1'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='外部工具副作用账本';

CREATE TABLE sq_budget_reservation
(
    id                BIGINT       NOT NULL AUTO_INCREMENT COMMENT '存储主键ID',
    reservation_id    CHAR(36)     NOT NULL COMMENT '预留ID',
    user_id           CHAR(36)     NOT NULL COMMENT '资源归属用户ID',
    task_id           CHAR(36)     NOT NULL COMMENT '目标ID',
    usage_key         VARCHAR(200) NOT NULL COMMENT '模型或工具真实请求稳定键',
    kind              VARCHAR(16)  NOT NULL COMMENT 'MODEL/TOOL',
    reserved_units    BIGINT       NOT NULL DEFAULT 0 COMMENT '预留整数微单位',
    settled_units     BIGINT       NOT NULL DEFAULT 0 COMMENT '已结算整数微单位',
    input_tokens      BIGINT       NOT NULL DEFAULT 0 COMMENT '输入token累计',
    output_tokens     BIGINT       NOT NULL DEFAULT 0 COMMENT '输出token累计',
    status            VARCHAR(16)  NOT NULL COMMENT 'RESERVED/SETTLED/UNKNOWN',
    version           BIGINT       NOT NULL DEFAULT 0 COMMENT '结算版本',
    creator_id        VARCHAR(36)  NOT NULL DEFAULT '' COMMENT '创建人ID',
    creator_name      VARCHAR(50)  NOT NULL DEFAULT '' COMMENT '创建人姓名',
    create_time       DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    last_updater_id   VARCHAR(36)  NOT NULL DEFAULT '' COMMENT '最后更新人ID',
    last_updater_name VARCHAR(50)  NOT NULL DEFAULT '' COMMENT '最后更新人姓名',
    last_update_time  DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '最后更新时间',
    del_flag          CHAR(1)      NOT NULL DEFAULT '0' COMMENT '删除标志[0-否,1-是]',
    PRIMARY KEY (id),
    UNIQUE KEY uk_reservation (reservation_id),
    UNIQUE KEY uk_usage (user_id, task_id, usage_key),
    CONSTRAINT ck_budget_reservation_del CHECK (del_flag IN ('0', '1'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='全局预算预留及可信结算';

-- D2 本地模拟外部系统：只用于受控写入与故障注入验证，生产由真实连接器替换。
CREATE TABLE sq_mock_ticket
(
    id           BIGINT        NOT NULL AUTO_INCREMENT COMMENT '存储主键ID',
    user_id      CHAR(36)      NOT NULL COMMENT '资源归属用户ID',
    external_key VARCHAR(200)  NOT NULL COMMENT '外部幂等键；同键同内容复用原单',
    request_hash VARCHAR(64)   NOT NULL COMMENT '请求参数摘要',
    title        VARCHAR(200)  NOT NULL COMMENT '工单标题',
    detail       VARCHAR(2000) NOT NULL COMMENT '工单描述',
    create_time  DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_mock_ticket_key (user_id, external_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='本地模拟外部工单系统';
